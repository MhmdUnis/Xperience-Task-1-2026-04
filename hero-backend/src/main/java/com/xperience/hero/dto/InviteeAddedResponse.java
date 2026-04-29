package com.xperience.hero.dto;

public class InviteeAddedResponse {
    private final Long invitationId;
    private final String email;
    private final String inviteeToken;

    public InviteeAddedResponse(Long invitationId, String email, String inviteeToken) {
        this.invitationId = invitationId;
        this.email = email;
        this.inviteeToken = inviteeToken;
    }

    public Long getInvitationId() { return invitationId; }
    public String getEmail() { return email; }
    public String getInviteeToken() { return inviteeToken; }
}
