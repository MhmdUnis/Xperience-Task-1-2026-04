package com.xperience.hero.dto;

public class EventCreatedResponse {
    private final Long eventId;
    private final String hostToken;

    public EventCreatedResponse(Long eventId, String hostToken) {
        this.eventId = eventId;
        this.hostToken = hostToken;
    }

    public Long getEventId() { return eventId; }
    public String getHostToken() { return hostToken; }
}
