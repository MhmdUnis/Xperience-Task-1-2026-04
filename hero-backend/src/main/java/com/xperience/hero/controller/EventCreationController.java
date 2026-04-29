package com.xperience.hero.controller;

import com.xperience.hero.dto.CreateEventRequest;
import com.xperience.hero.dto.EventCreatedResponse;
import com.xperience.hero.entity.Event;
import com.xperience.hero.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventCreationController {

    private final EventService eventService;

    public EventCreationController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventCreatedResponse> create(@RequestBody CreateEventRequest req) {
        Event event = eventService.createEvent(
                req.getTitle(),
                req.getDescription(),
                req.getLocation(),
                req.getStartTime(),
                req.getCapacity()
        );
        return ResponseEntity.ok(new EventCreatedResponse(event.getId(), event.getHostToken()));
    }
}
