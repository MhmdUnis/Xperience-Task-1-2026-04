package com.xperience.hero.dto;

import java.time.LocalDateTime;

public class InviteeViewResponse {
    private final String eventTitle;
    private final String eventDescription;
    private final String eventLocation;
    private final LocalDateTime startTime;
    private final String eventState;     // OPEN | CLOSED | CANCELED
    private final boolean locked;        // now >= startTime
    private final String email;
    private final String myStatus;       // NO_RESPONSE | YES | NO | MAYBE
    private final String myClassification; // CONFIRMED | WAITLISTED | null
    private final Long myWaitlistSeq;    // null unless waitlisted

    public InviteeViewResponse(String eventTitle, String eventDescription, String eventLocation,
                               LocalDateTime startTime, String eventState, boolean locked,
                               String email, String myStatus, String myClassification,
                               Long myWaitlistSeq) {
        this.eventTitle = eventTitle;
        this.eventDescription = eventDescription;
        this.eventLocation = eventLocation;
        this.startTime = startTime;
        this.eventState = eventState;
        this.locked = locked;
        this.email = email;
        this.myStatus = myStatus;
        this.myClassification = myClassification;
        this.myWaitlistSeq = myWaitlistSeq;
    }

    public String getEventTitle() { return eventTitle; }
    public String getEventDescription() { return eventDescription; }
    public String getEventLocation() { return eventLocation; }
    public LocalDateTime getStartTime() { return startTime; }
    public String getEventState() { return eventState; }
    public boolean isLocked() { return locked; }
    public String getEmail() { return email; }
    public String getMyStatus() { return myStatus; }
    public String getMyClassification() { return myClassification; }
    public Long getMyWaitlistSeq() { return myWaitlistSeq; }
}
