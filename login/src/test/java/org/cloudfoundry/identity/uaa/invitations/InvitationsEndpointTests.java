package org.cloudfoundry.identity.uaa.invitations;

import org.cloudfoundry.identity.uaa.error.UaaException;
import org.cloudfoundry.identity.uaa.login.util.SecurityUtils;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUserProvisioning;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class InvitationsEndpointTests {

    private InvitationsEndpoint endpoint;
    private InvitationsService invitationsService;
    private ScimUserProvisioning userProvisioning;

    @Before
    public void resetSecurityContext() {
        SecurityContextHolder.clearContext();

        Authentication auth = SecurityUtils.oauthAuthenticatedClient("marissa", null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // created mock invitationsservice
        invitationsService = mock(InvitationsService.class);
        userProvisioning = mock(ScimUserProvisioning.class);

        // created and instance of endpoint with mock invite service
        endpoint = new InvitationsEndpoint(invitationsService, userProvisioning);
    }

    @Test
    public void singleValidEmail() {
        ScimUser user = new ScimUser();
        user.setId("12345");
        when(userProvisioning.query(anyString())).thenReturn(Collections.singletonList(user));

        // called the endpoint inviteUsers
        ResponseEntity<InvitationsResponse> response = endpoint.inviteUsers(new InvitationsRequest(new String[] {"user@example.com"}), "acme-client", "example.com");
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertThat(response.getBody().getNewInvites().size(), is(1));
        assertThat(response.getBody().getNewInvites().get(0), is("12345"));
        assertThat(response.getBody().getAlreadyInvited().size(), is(0));
        assertThat(response.getBody().getFailedInvites().size(), is(0));

        // verified that the invite service had been called
        verify(invitationsService, times(1)).inviteUser("user@example.com", "marissa", "acme-client", "example.com");
    }

    @Test
    public void multipleValidEmails() {
        ScimUser user = new ScimUser();
        user.setId("12345");
        ScimUser user2 = new ScimUser();
        user2.setId("user-2-id");
        List<ScimUser> scimUsers = new ArrayList<>();
        scimUsers.add(user);
        scimUsers.add(user2);

        when(userProvisioning.query(anyString())).thenReturn(scimUsers);

        // called the endpoint inviteUsers
        ResponseEntity<InvitationsResponse> response = endpoint.inviteUsers(new InvitationsRequest(new String[] {"user@example.com", "another@example.com"}), "acme-client", "example.com");
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertThat(response.getBody().getNewInvites().size(), is(2));
        assertThat(response.getBody().getNewInvites().contains(user.getId()), is(true));
        assertThat(response.getBody().getNewInvites().contains(user2.getId()), is(true));
        assertThat(response.getBody().getAlreadyInvited().size(), is(0));
        assertThat(response.getBody().getFailedInvites().size(), is(0));

        // verified that the invite service had been called
        verify(invitationsService, times(1)).inviteUser("user@example.com", "marissa", "acme-client", "example.com");
        verify(invitationsService, times(1)).inviteUser("another@example.com", "marissa", "acme-client", "example.com");
        verifyNoMoreInteractions(invitationsService);
    }

    @Test
    public void multipleEmailsFailure() {
        ScimUser user = new ScimUser();
        user.setId("12345");

        String invalidEmail = "doesnotexist@example.com";

        doThrow(new UaaException("User already verified.")).when(invitationsService).inviteUser(invalidEmail, "marissa", "acme-client", "example.com");
        when(userProvisioning.query(anyString())).thenReturn(Collections.singletonList(user));

        // called the endpoint inviteUsers
        ResponseEntity<InvitationsResponse> response = endpoint.inviteUsers(new InvitationsRequest(new String[] {"user@example.com", "doesnotexist@example.com"}), "acme-client", "example.com");
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertThat(response.getBody().getNewInvites().size(), is(1));
        assertThat(response.getBody().getNewInvites().contains(user.getId()), is(true));
        assertThat(response.getBody().getAlreadyInvited().size(), is(0));
        assertThat(response.getBody().getFailedInvites().size(), is(1));
        assertThat(response.getBody().getFailedInvites().contains(invalidEmail), is(true));

        // verified that the invite service had been called
        verify(invitationsService, times(1)).inviteUser("user@example.com", "marissa", "acme-client", "example.com");
        verify(invitationsService, times(1)).inviteUser(invalidEmail, "marissa", "acme-client", "example.com");
        verifyNoMoreInteractions(invitationsService);
    }
}

