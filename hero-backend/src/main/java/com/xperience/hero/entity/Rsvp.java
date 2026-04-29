package com.xperience.hero.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "rsvps",
    schema = "hero",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rsvp_event_email", columnNames = {"event_id", "email_lower"}),
        @UniqueConstraint(name = "uk_rsvp_token", columnNames = {"invitee_token"})
    }
)
public class Rsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 320)
    private String email;

    // lowercased copy of email used purely for the unique constraint
    @Column(name = "email_lower", nullable = false, length = 320)
    private String emailLower;

    @Column(name = "invitee_token", nullable = false, length = 64)
    private String inviteeToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RsvpStatus status = RsvpStatus.NO_RESPONSE;

    // null unless this RSVP is currently waitlisted; per-event monotonic FIFO sequence
    @Column(name = "waitlist_seq")
    private Long waitlistSeq;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Rsvp() {}

    public Rsvp(Event event, String email, String inviteeToken) {
        this.event = event;
        this.email = email;
        this.emailLower = email.toLowerCase();
        this.inviteeToken = inviteeToken;
    }

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public String getEmail() { return email; }
    public String getInviteeToken() { return inviteeToken; }
    public RsvpStatus getStatus() { return status; }
    public Long getWaitlistSeq() { return waitlistSeq; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(RsvpStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setWaitlistSeq(Long waitlistSeq) {
        this.waitlistSeq = waitlistSeq;
    }

    public boolean isWaitlisted() {
        return status == RsvpStatus.YES && waitlistSeq != null;
    }

    public boolean isConfirmed() {
        return status == RsvpStatus.YES && waitlistSeq == null;
    }
}
