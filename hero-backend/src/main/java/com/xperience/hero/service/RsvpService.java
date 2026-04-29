package com.xperience.hero.service;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.EventState;
import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.entity.RsvpStatus;
import com.xperience.hero.exception.BadRequestException;
import com.xperience.hero.exception.ForbiddenException;
import com.xperience.hero.exception.NotFoundException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.RsvpRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RsvpService {

    private final RsvpRepository rsvpRepository;
    private final EventRepository eventRepository;

    public RsvpService(RsvpRepository rsvpRepository, EventRepository eventRepository) {
        this.rsvpRepository = rsvpRepository;
        this.eventRepository = eventRepository;
    }

    public Rsvp findByInviteeToken(String inviteeToken) {
        return rsvpRepository.findByInviteeToken(inviteeToken)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
    }

    /**
     * Single transactional path for an RSVP transition.
     * Acquires a pessimistic lock on the event row, then:
     *   1. Re-checks event state and start-time boundary (>= rejects).
     *   2. Computes the new state.
     *   3. Frees a confirmed slot if needed and promotes the FIFO waitlist head in the same transaction.
     *   4. Persists the new RSVP state.
     */
    @Transactional
    public Rsvp setRsvp(String inviteeToken, RsvpStatus desired) {
        if (desired == null || desired == RsvpStatus.NO_RESPONSE) {
            throw new BadRequestException("Response must be YES, NO, or MAYBE");
        }

        Rsvp rsvp = findByInviteeToken(inviteeToken);

        // Lock the event row for the duration of this transaction.
        Event event = eventRepository.findByIdForUpdate(rsvp.getEvent().getId())
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() == EventState.CANCELED) {
            throw new ForbiddenException("Event has been canceled");
        }
        if (event.getState() == EventState.CLOSED) {
            throw new ForbiddenException("Event is closed to further responses");
        }
        // Lazy start-time lock check (now >= start_time => rejected).
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(event.getStartTime())) {
            throw new ForbiddenException("Event has started; RSVPs are locked");
        }

        boolean wasConfirmed = rsvp.isConfirmed();
        boolean wasWaitlisted = rsvp.isWaitlisted();

        // Idempotent absolute-set: same desired status applied twice is a no-op.
        if (rsvp.getStatus() == desired && desired != RsvpStatus.YES) {
            return rsvp;
        }

        // Apply the transition.
        if (desired == RsvpStatus.YES) {
            if (wasConfirmed || wasWaitlisted) {
                // Already a YES; nothing to change.
                return rsvp;
            }
            long confirmed = rsvpRepository.countConfirmed(event);
            if (confirmed < event.getCapacity()) {
                rsvp.setStatus(RsvpStatus.YES);
                rsvp.setWaitlistSeq(null);
            } else {
                Long maxSeq = rsvpRepository.maxWaitlistSeq(event);
                long nextSeq = (maxSeq == null ? 1L : maxSeq + 1L);
                rsvp.setStatus(RsvpStatus.YES);
                rsvp.setWaitlistSeq(nextSeq);
            }
        } else {
            // NO or MAYBE: clear waitlist membership; set status.
            rsvp.setStatus(desired);
            rsvp.setWaitlistSeq(null);

            // If we just freed a confirmed slot, promote the FIFO waitlist head.
            if (wasConfirmed) {
                List<Rsvp> head = rsvpRepository.findWaitlistHead(event, PageRequest.of(0, 1));
                if (!head.isEmpty()) {
                    Rsvp promoted = head.get(0);
                    promoted.setWaitlistSeq(null);
                    // status stays YES; classification flips to confirmed via null waitlistSeq
                    rsvpRepository.save(promoted);
                }
            }
        }

        return rsvpRepository.save(rsvp);
    }
}
