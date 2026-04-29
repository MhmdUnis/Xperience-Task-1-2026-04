package com.xperience.hero.dto;

import com.xperience.hero.entity.Rsvp;

public class InviteeRowDto {
    private final String email;
    private final String status;        // NO_RESPONSE | YES | NO | MAYBE
    private final String classification; // CONFIRMED | WAITLISTED | null
    private final Long waitlistSeq;     // null if not waitlisted
    private final String inviteeToken;

    public InviteeRowDto(String email, String status, String classification,
                         Long waitlistSeq, String inviteeToken) {
        this.email = email;
        this.status = status;
        this.classification = classification;
        this.waitlistSeq = waitlistSeq;
        this.inviteeToken = inviteeToken;
    }

    public static InviteeRowDto from(Rsvp r) {
        String classification = null;
        if (r.isConfirmed()) classification = "CONFIRMED";
        else if (r.isWaitlisted()) classification = "WAITLISTED";
        return new InviteeRowDto(
            r.getEmail(),
            r.getStatus().name(),
            classification,
            r.getWaitlistSeq(),
            r.getInviteeToken()
        );
    }

    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getClassification() { return classification; }
    public Long getWaitlistSeq() { return waitlistSeq; }
    public String getInviteeToken() { return inviteeToken; }
}
