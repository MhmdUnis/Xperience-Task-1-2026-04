package com.xperience.hero.controller;

import com.xperience.hero.dto.InviteeViewResponse;
import com.xperience.hero.dto.RsvpRequest;
import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.entity.RsvpStatus;
import com.xperience.hero.exception.BadRequestException;
import com.xperience.hero.service.RsvpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/invite/{inviteeToken}")
public class InviteeController {

    private final RsvpService rsvpService;

    public InviteeController(RsvpService rsvpService) {
        this.rsvpService = rsvpService;
    }

    @GetMapping
    public ResponseEntity<InviteeViewResponse> view(@PathVariable String inviteeToken) {
        Rsvp r = rsvpService.findByInviteeToken(inviteeToken);
        return ResponseEntity.ok(toView(r));
    }

    @PutMapping("/rsvp")
    public ResponseEntity<InviteeViewResponse> respond(@PathVariable String inviteeToken,
                                                       @RequestBody RsvpRequest req) {
        if (req.getResponse() == null) {
            throw new BadRequestException("response is required (YES, NO, MAYBE)");
        }
        RsvpStatus desired;
        try {
            desired = RsvpStatus.valueOf(req.getResponse().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid response value");
        }
        Rsvp r = rsvpService.setRsvp(inviteeToken, desired);
        return ResponseEntity.ok(toView(r));
    }

    private InviteeViewResponse toView(Rsvp r) {
        Event event = r.getEvent();
        boolean locked = !LocalDateTime.now().isBefore(event.getStartTime());
        String classification = null;
        if (r.isConfirmed()) classification = "CONFIRMED";
        else if (r.isWaitlisted()) classification = "WAITLISTED";

        return new InviteeViewResponse(
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getStartTime(),
            event.getState().name(),
            locked,
            r.getEmail(),
            r.getStatus().name(),
            classification,
            r.getWaitlistSeq()
        );
    }
}
