package com.xperience.hero.repository;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.entity.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RsvpRepository extends JpaRepository<Rsvp, Long> {

    Optional<Rsvp> findByInviteeToken(String inviteeToken);

    List<Rsvp> findByEventOrderByCreatedAtAsc(Event event);

    boolean existsByEventAndEmailLower(Event event, String emailLower);

    // Confirmed = YES with waitlistSeq == null
    @Query("SELECT COUNT(r) FROM Rsvp r WHERE r.event = :event AND r.status = com.xperience.hero.entity.RsvpStatus.YES AND r.waitlistSeq IS NULL")
    long countConfirmed(@Param("event") Event event);

    @Query("SELECT COUNT(r) FROM Rsvp r WHERE r.event = :event AND r.status = com.xperience.hero.entity.RsvpStatus.YES AND r.waitlistSeq IS NOT NULL")
    long countWaitlisted(@Param("event") Event event);

    long countByEventAndStatus(Event event, RsvpStatus status);

    // Largest current waitlist sequence for the event, or null if waitlist empty.
    @Query("SELECT MAX(r.waitlistSeq) FROM Rsvp r WHERE r.event = :event AND r.waitlistSeq IS NOT NULL")
    Long maxWaitlistSeq(@Param("event") Event event);

    // FIFO head of the waitlist for promotion.
    @Query("SELECT r FROM Rsvp r WHERE r.event = :event AND r.status = com.xperience.hero.entity.RsvpStatus.YES AND r.waitlistSeq IS NOT NULL ORDER BY r.waitlistSeq ASC")
    List<Rsvp> findWaitlistHead(@Param("event") Event event, org.springframework.data.domain.Pageable pageable);
}
