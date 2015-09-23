package org.cloudfoundry.identity.uaa.invitations;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class InvitationsResponse {

    @JsonProperty(value="new_invites")
    private List<String> newInvites = new ArrayList<>();
    @JsonProperty(value="already_invited")
    private List<String> alreadyInvited = new ArrayList<>();
    @JsonProperty(value="failed_invites")
    private List<String> failedInvites = new ArrayList<>();

    public InvitationsResponse() {}

    public List<String> getNewInvites() {
        return newInvites;
    }

    public void setNewInvites(List<String> newInvites) {
        this.newInvites = newInvites;
    }

    public List<String> getAlreadyInvited() {
        return alreadyInvited;
    }

    public void setAlreadyInvited(List<String> alreadyInvited) {
        this.alreadyInvited = alreadyInvited;
    }

    public List<String> getFailedInvites() {
        return failedInvites;
    }

    public void setFailedInvites(List<String> failedInvites) {
        this.failedInvites = failedInvites;
    }

}
