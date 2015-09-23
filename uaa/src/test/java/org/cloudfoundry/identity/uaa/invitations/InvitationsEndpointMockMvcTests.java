package org.cloudfoundry.identity.uaa.invitations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.identity.uaa.login.EmailService;
import org.cloudfoundry.identity.uaa.login.util.FakeJavaMailSender;
import org.cloudfoundry.identity.uaa.mock.InjectedMockContextTest;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.scim.ScimGroup;
import org.cloudfoundry.identity.uaa.scim.ScimGroupMember;
import org.cloudfoundry.identity.uaa.scim.ScimGroupMembershipManager;
import org.cloudfoundry.identity.uaa.scim.ScimGroupProvisioning;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.exception.MemberAlreadyExistsException;
import org.cloudfoundry.identity.uaa.test.TestClient;
import org.cloudfoundry.identity.uaa.util.ClientUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.mail.Message;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.cloudfoundry.identity.uaa.util.ClientUtils.createScimClient;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.cloudfoundry.identity.uaa.login.util.FakeJavaMailSender.MimeMessageWrapper;

public class InvitationsEndpointMockMvcTests extends InjectedMockContextTest {

    private String scimInviteToken;
    private TestClient testClient;
    private RandomValueStringGenerator generator = new RandomValueStringGenerator();
    private String clientId;
    private String clientSecret;
    private String adminToken;
    private String authorities;
    private FakeJavaMailSender fakeJavaMailSender = new FakeJavaMailSender();
    private JavaMailSender originalSender;

    @Before
    public void setUp() throws Exception {
        testClient = new TestClient(getMockMvc());
        adminToken = testClient.getClientCredentialsOAuthAccessToken("admin", "adminsecret", "clients.read clients.write clients.secret scim.read scim.write");
        clientId = generator.generate().toLowerCase();
        clientSecret = generator.generate().toLowerCase();
        authorities = "scim.read,scim.invite";
        createScimClient(this.getMockMvc(), adminToken, clientId, clientSecret, "oauth", "scim.read,scim.invite", Arrays.asList(new ClientUtils.GrantType[] {ClientUtils.GrantType.client_credentials, ClientUtils.GrantType.password}), authorities);
        scimInviteToken = testClient.getClientCredentialsOAuthAccessToken(clientId, clientSecret,"scim.read scim.invite");
    }

    @Before
    public void setUpFakeMailServer() throws Exception {
        originalSender = getWebApplicationContext().getBean("emailService", EmailService.class).getMailSender();
        getWebApplicationContext().getBean("emailService", EmailService.class).setMailSender(fakeJavaMailSender);
    }

    @After
    public void restoreMailServer() throws Exception {
        getWebApplicationContext().getBean("emailService", EmailService.class).setMailSender(originalSender);
    }

    @Test
    public void testInviteUserWithClientCredentials() throws Exception {
        String email = "user1@example.com";
        sendRequestWithToken(scimInviteToken, email);
    }

    @Test
    public void testInviteMultipleUsersWithClientCredentials() throws Exception {
        String[] emails = new String[] {"user1@example.com", "user2@example.com"};
        sendRequestWithToken(scimInviteToken, emails);
    }

    @Test
    public void testInviteUserWithUserCredentials() throws Exception {

        // create a user (with the required permissions) to perform the actual /invite_users action
        String username = generator.generate();
        ScimUser user = new ScimUser(clientId, username, "given-name", "family-name");
        user.setPrimaryEmail("email@example.com");
        user.setPassword("password");
        MockMvcUtils mockMvcUtils = new MockMvcUtils();
        user = mockMvcUtils.createUser(this.getMockMvc(), adminToken, user);

        for (String scope : new String[] {"scim.read", "scim.invite"}) {
            ScimGroupMember member = new ScimGroupMember(user.getId(), ScimGroupMember.Type.USER, Arrays.asList(ScimGroupMember.Role.READER));

            ScimGroup group = mockMvcUtils.getGroup(getMockMvc(), adminToken, scope);
            group.getMembers().add(member);
            mockMvcUtils.updateGroup(getMockMvc(), adminToken, group);
            user.getGroups().add(new ScimUser.Group(group.getId(), scope));
        }

        // get a bearer token for the user
        String userToken = testClient.getUserOAuthAccessToken(clientId, clientSecret, user.getUserName(), "password", "scim.read scim.invite");

        // perform the actual invite
        sendRequestWithToken(userToken, "user1@example.com");
    }

    // TODO: Test multiple invited users, existing verified users, existing unverified users, null emails

    private void sendRequestWithToken(String token, String...emails) throws Exception {
        InvitationsRequest invitations = new InvitationsRequest(emails);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        byte[] requestBody = objectMapper.writeValueAsBytes(invitations);

        MvcResult result = getMockMvc().perform(post("/invite_users?client-id=" + clientId + "&redirect-uri=example.com")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

        InvitationsResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), InvitationsResponse.class);
        assertThat(response.getNewInvites().size(), is(emails.length));
        assertThat(response.getAlreadyInvited().size(), is(0));
        assertThat(response.getFailedInvites().size(), is(0));

        assertEquals(emails.length, fakeJavaMailSender.getSentMessages().size());
        for (int i=0; i < emails.length; i++) {
            MimeMessageWrapper mimeMessageWrapper = fakeJavaMailSender.getSentMessages().get(i);
            assertEquals(1, mimeMessageWrapper.getRecipients(Message.RecipientType.TO).size());
            assertEquals(emails[i], mimeMessageWrapper.getRecipients(Message.RecipientType.TO).get(0).toString());
        }
    }

}
