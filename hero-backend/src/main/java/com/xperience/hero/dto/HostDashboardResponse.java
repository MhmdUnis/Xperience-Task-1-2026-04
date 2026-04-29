package com.xperience.hero.dto;

import java.time.LocalDateTime;
import java.util.List;

public class HostDashboardResponse {
    private final Long eventId;
    private final String title;
    private final String description;
    private final String location;
    private final LocalDateTime startTime;
    private final Integer capacity;
    private final String state;          // OPEN | CLOSED | CANCELED
    private final boolean locked;        // derived: now >= startTime
    private final Counts counts;
    private final List<InviteeRowDto> invitees;

    public HostDashboardResponse(Long eventId, String title, String description, String location,
                                 LocalDateTime startTime, Integer capacity, String state,
                                 boolean locked, Counts counts, List<InviteeRowDto> invitees) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.capacity = capacity;
        this.state = state;
        this.locked = locked;
        this.counts = counts;
        this.invitees = invitees;
    }

    public Long getEventId() { return eventId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public LocalDateTime getStartTime() { return startTime; }
    public Integer getCapacity() { return capacity; }
    public String getState() { return state; }
    public boolean isLocked() { return locked; }
    public Counts getCounts() { return counts; }
    public List<InviteeRowDto> getInvitees() { return invitees; }

    public static class Counts {
        private final long confirmed;
        private final long waitlisted;
        private final long no;
        private final long maybe;
        private final long noResponse;

        public Counts(long confirmed, long waitlisted, long no, long maybe, long noResponse) {
            this.confirmed = confirmed;
            this.waitlisted = waitlisted;
            this.no = no;
            this.maybe = maybe;
            this.noResponse = noResponse;
        }
        public long getConfirmed() { return confirmed; }
        public long getWaitlisted() { return waitlisted; }
        public long getNo() { return no; }
        public long getMaybe() { return maybe; }
        public long getNoResponse() { return noResponse; }
    }
}
