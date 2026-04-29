package com.xperience.hero.service;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.EventState;
import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.exception.ConflictException;
import com.xperience.hero.exception.ForbiddenException;
import com.xperience.hero.exception.NotFoundException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.RsvpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final RsvpRepository rsvpRepository;
    private final TokenService tokenService;

    public EventService(EventRepository eventRepository,
                        RsvpRepository rsvpRepository,
                        TokenService tokenService) {
        this.eventRepository = eventRepository;
        this.rsvpRepository = rsvpRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public Event createEvent(String title, String description, String location,
                             LocalDateTime startTime, Integer capacity) {
        if (capacity == null || capacity < 1) {
            throw new ConflictException("Capacity must be at least 1");
        }
        if (startTime == null || startTime.isBefore(LocalDateTime.now())) {
            throw new ConflictException("Start time must be in the future");
        }
        Event e = new Event(title, description, location, startTime, capacity, tokenService.newToken());
        return eventRepository.save(e);
    }

    public Event requireByHostToken(String hostToken) {
        return eventRepository.findByHostToken(hostToken)
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    @Transactional
    public Rsvp addInvitee(String hostToken, String email) {
        Event event = requireByHostToken(hostToken);
        if (event.getState() != EventState.OPEN) {
            throw new ForbiddenException("Event is not accepting new invitees");
        }
        if (rsvpRepository.existsByEventAndEmailLower(event, email.toLowerCase())) {
            throw new ConflictException("This email is already invited");
        }
        Rsvp rsvp = new Rsvp(event, email, tokenService.newToken());
        return rsvpRepository.save(rsvp);
    }

    @Transactional
    public Event closeEvent(String hostToken) {
        Event event = lockEventByHostToken(hostToken);
        if (event.getState() == EventState.CANCELED) {
            throw new ConflictException("Event is canceled");
        }
        event.setState(EventState.CLOSED);
        return event;
    }

    @Transactional
    public Event reopenEvent(String hostToken) {
        Event event = lockEventByHostToken(hostToken);
        if (event.getState() != EventState.CLOSED) {
            throw new ConflictException("Only a closed event can be reopened");
        }
        if (!LocalDateTime.now().isBefore(event.getStartTime())) {
            throw new ConflictException("Event has already started");
        }
        event.setState(EventState.OPEN);
        return event;
    }

    @Transactional
    public Event cancelEvent(String hostToken) {
        Event event = lockEventByHostToken(hostToken);
        if (event.getState() == EventState.CANCELED) {
            throw new ConflictException("Event is already canceled");
        }
        event.setState(EventState.CANCELED);
        return event;
    }

    public List<Rsvp> listInvitees(Event event) {
        return rsvpRepository.findByEventOrderByCreatedAtAsc(event);
    }

    private Event lockEventByHostToken(String hostToken) {
        Event found = requireByHostToken(hostToken);
        return eventRepository.findByIdForUpdate(found.getId())
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
