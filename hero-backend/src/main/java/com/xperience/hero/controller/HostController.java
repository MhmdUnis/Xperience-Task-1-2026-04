package com.xperience.hero.controller;

import com.xperience.hero.dto.AddInviteeRequest;
import com.xperience.hero.dto.HostDashboardResponse;
import com.xperience.hero.dto.InviteeAddedResponse;
import com.xperience.hero.dto.InviteeRowDto;
import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.entity.RsvpStatus;
import com.xperience.hero.exception.BadRequestException;
import com.xperience.hero.repository.RsvpRepository;
import com.xperience.hero.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/host/{hostToken}")
public class HostController {

    private final EventService eventService;
    private final RsvpRepository rsvpRepository;

    public HostController(EventService eventService, RsvpRepository rsvpRepository) {
        this.eventService = eventService;
        this.rsvpRepository = rsvpRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<HostDashboardResponse> dashboard(@PathVariable String hostToken) {
        Event event = eventService.requireByHostToken(hostToken);
        List<Rsvp> invitees = eventService.listInvitees(event);

        long confirmed = rsvpRepository.countConfirmed(event);
        long waitlisted = rsvpRepository.countWaitlisted(event);
        long no = rsvpRepository.countByEventAndStatus(event, RsvpStatus.NO);
        long maybe = rsvpRepository.countByEventAndStatus(event, RsvpStatus.MAYBE);
        long noResp = rsvpRepository.countByEventAndStatus(event, RsvpStatus.NO_RESPONSE);

        boolean locked = !LocalDateTime.now().isBefore(event.getStartTime());

        HostDashboardResponse.Counts counts =
            new HostDashboardResponse.Counts(confirmed, waitlisted, no, maybe, noResp);

        List<InviteeRowDto> rows = invitees.stream().map(InviteeRowDto::from).toList();

        HostDashboardResponse body = new HostDashboardResponse(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getStartTime(),
            event.getCapacity(),
            event.getState().name(),
            locked,
            counts,
            rows
        );
        return ResponseEntity.ok(body);
    }

    @PostMapping("/invitees")
    public ResponseEntity<InviteeAddedResponse> addInvitee(@PathVariable String hostToken,
                                                           @RequestBody AddInviteeRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new BadRequestException("Email is required");
        }
        Rsvp r = eventService.addInvitee(hostToken, req.getEmail().trim());
        return ResponseEntity.ok(new InviteeAddedResponse(r.getId(), r.getEmail(), r.getInviteeToken()));
    }

    @PostMapping("/close")
    public ResponseEntity<Void> close(@PathVariable String hostToken) {
        eventService.closeEvent(hostToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reopen")
    public ResponseEntity<Void> reopen(@PathVariable String hostToken) {
        eventService.reopenEvent(hostToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String hostToken) {
        eventService.cancelEvent(hostToken);
        return ResponseEntity.noContent().build();
    }
}
