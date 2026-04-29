package com.xperience.hero.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events", schema = "hero")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, unique = true, length = 64)
    private String hostToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventState state = EventState.OPEN;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Event() {}

    public Event(String title, String description, String location,
                 LocalDateTime startTime, Integer capacity, String hostToken) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.capacity = capacity;
        this.hostToken = hostToken;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public LocalDateTime getStartTime() { return startTime; }
    public Integer getCapacity() { return capacity; }
    public String getHostToken() { return hostToken; }
    public EventState getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setState(EventState state) { this.state = state; }
}
