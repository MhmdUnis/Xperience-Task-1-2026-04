package com.xperience.hero.dto;

import java.time.LocalDateTime;

public class CreateEventRequest {
    private String title;
    private String description;
    private String location;
    private LocalDateTime startTime;
    private Integer capacity;

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public LocalDateTime getStartTime() { return startTime; }
    public Integer getCapacity() { return capacity; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setLocation(String location) { this.location = location; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
