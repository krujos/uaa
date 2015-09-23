package org.cloudfoundry.identity.uaa.invitations;

import org.aspectj.lang.annotation.Before;
import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.db.SqlUtils;
import org.cloudfoundry.identity.uaa.error.UaaException;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUserProvisioning;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.cloudfoundry.identity.uaa.db.SqlUtils.toQuotedAndCommaDelimitedString;

@Controller
public class InvitationsEndpoint {

    private InvitationsService invitationsService;
    private ScimUserProvisioning users;

    public InvitationsEndpoint(InvitationsService invitationsService, ScimUserProvisioning users) {
        this.invitationsService = invitationsService;
        this.users = users;
    }

    @RequestMapping(value="/invite_users", method= RequestMethod.POST, consumes="application/json")
    public ResponseEntity<InvitationsResponse> inviteUsers(@RequestBody InvitationsRequest invitations, @RequestParam(value="client-id") String clientId, @RequestParam(value="redirect-uri") String redirectUri) {

        // todo: get clientId from token, if not supplied in clientId

        // todo: investigate how to triangulate an existing user from their email, clientId and origin?
        //       but where do we get origin from?
        //       how to look up a user
        //       1. Get Client using clientId
        //       2. Get client's identity providers
        //       3. Spin thru each identity provider looking for the one that handles the domain (of the email being invited)
        //       4. Once identity provider has been found, look up it's 'origin key'
        //       5. Query Users table based on email and 'origin key'

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUser = null;
        if (principal instanceof UaaPrincipal)
            currentUser = ((UaaPrincipal)principal).getName();
        else if (principal instanceof String)
            currentUser = (String)principal;

        InvitationsResponse invitationsResponse = new InvitationsResponse();
        List<String> newInvitesEmails = new ArrayList<>();

        for (String email : invitations.getEmails()) {
            try {
                invitationsService.inviteUser(email, currentUser, clientId, redirectUri);
                newInvitesEmails.add(email);
            } catch (UaaException uaae) {
                invitationsResponse.getFailedInvites().add(email);
            }
        }

        // todo: Convert to use the ids endpoint
        String where = "";
        for (int i=0; i < newInvitesEmails.size(); i++) {
            if (i > 0)
                where += " OR ";
            where += "email eq \"" + newInvitesEmails.get(i) + "\"";
        }
        List<ScimUser> scimUsers = users.query(where);
        for (ScimUser user : scimUsers) {
            invitationsResponse.getNewInvites().add(user.getId());
        }

        return new ResponseEntity<>(invitationsResponse, HttpStatus.OK);
    }

}
