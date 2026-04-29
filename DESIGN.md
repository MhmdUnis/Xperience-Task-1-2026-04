# Event RSVP Manager — Design File

This document captures the high-level design for an Event RSVP Manager built on Spring Boot 4 + Spring Data JPA + PostgreSQL with a React 19 / TypeScript frontend. It is a working design — several decisions remain explicitly open, and they are flagged as such throughout. Where a tradeoff exists, both sides are recorded; where a mechanism is undecided, candidates are listed without a winner. The most exposed area today is the RSVP-lock mechanism at `start_time` (see *Risks and Failure Notes*).

**Document map**

1. Problem, Goals, Non-Goals
2. Context and Constraints
3. Confirmed Facts / Working Assumptions / Open Questions
4. Actors and Workflows
5. Invariants
6. Proposed Architecture
7. Data Ownership and State Model
8. Concurrency and Correctness Notes
9. Risks and Failure Notes
10. Alternatives Considered
11. Tradeoffs (explicit)
12. Rollout / Migration Notes
13. Open Questions Recap

## Problem

Event organizers today struggle with visibility into attendance commitments. When planning an event, they have no reliable way to track who intends to attend, who has declined, or whether people might still change their minds. This lack of visibility creates several cascading problems: they cannot reliably enforce capacity limits, risking overbooking and logistical failure; they must coordinate attendance manually through emails and spreadsheets, creating coordination burden and error; and they remain uncertain about final attendance counts until the event is imminent, making it impossible to plan resources, messaging, or logistics with confidence.

## Goals

- Organizers can create events and invite people by email
- Organizers have real-time visibility into attendance counts and attendee lists
- Attendees can submit and change RSVP responses (Yes/No/Maybe) before event start
- Capacity limits are enforced automatically; excess Yes responses go to waitlist
- Responses are locked and immutable after event start time

## Non-Goals

- User authentication, account management, or login systems
- Event modification or cancellation after creation (create once, cannot edit)
- Email campaigns, notifications, or bulk invitation imports
- Payment, ticketing, or financial transactions
- Post-event analytics, surveys, or feedback collection

## Context and Constraints

**Technical**
- Database must enforce capacity and waitlist atomicity within a single transaction — prevents race conditions where multiple RSVP changes could cause overbooking
- Invitees authenticate via unique token, not login accounts — shapes authorization layer, no session management needed
- Hosts access events via unique token/link — same token-based auth pattern, no registration system
- Spring Boot 4 / Spring Data JPA / PostgreSQL — constrains transaction handling and ORM patterns; must use native transactions or pessimistic locking for capacity enforcement

**Product**
- Events are immutable after creation — no edit workflows, no cascade effects from date/capacity changes
- Automatic waitlist promotion on RSVP changes — forces transactional consistency, rules out async/eventual consistency patterns
- Capacity and waitlist are core features, not optional — every RSVP state transition must check capacity
- Three-state RSVP model (Yes/No/Maybe) — shapes state machine and count calculations
- Hosts can cancel events or close them to further responses — requires event-level state management (locked vs open)

**Operational**
- Unique token generation and management must be secure and unforgeable — shapes how tokens are created and stored
- Host dashboard must show current state without polling delays — real-time or near-real-time requirement

**Organizational**
- Educational learning project with provided tech stack — no technology choices available; focus is on design discipline, not tool selection

## Confirmed Facts

- Tech stack: Spring Boot 4, Spring Data JPA, PostgreSQL, React 19, TypeScript
- Creators automatically become event hosts
- Invitees receive unique links to respond
- RSVP options: Yes / No / Maybe
- Host sees dashboard with counts and attendee list
- When max capacity is reached, new "Yes" → waitlist
- Waitlisted person auto-moves to confirmed if confirmed person changes to "No"
- Invitees can change RSVP before event starts
- After event start time, RSVPs are locked
- Host can cancel or close event to further responses
- Database must enforce capacity atomically (no overbooking)

## Working Assumptions

- Hosts access events via unique token (no login/accounts)
- Events are immutable after creation (no edits to date, capacity, location) — architectural load-bearing assumption
- Invitations are link-based, not sent via email service
- Single host per event
- Waitlist uses FIFO: first person waitlisted is first promoted
- Host and invitee identity both use token-based auth
- Token generation is secure and unforgeable

## Open Questions by Impact

**CORRECTNESS 🔴**
- What exactly triggers the RSVP lock? (Exactly at event start time? Before? Manual trigger?)
- When a waitlisted person changes to "No", are they removed from the waitlist?
- What happens to waitlist entries if the event is canceled?
- What is the exact behavioral difference between "cancel" and "close to RSVPs"?
- Can the same email be invited twice to the same event?
- Are there intermediate event states (draft, published, started, ended) or just open/closed/canceled?

**SECURITY 🔒**
- Do unique tokens expire, or are they permanent?
- How does a host retrieve/access their own events after creation?

**CONCURRENCY ⚡**
- (All correctness questions above also affect concurrent safety of RSVP state transitions)

## Actors and Workflows

### Actors

- **Host** — creates events, views dashboard, can cancel or close events to further RSVPs. Authenticated via unique host token.
- **Invitee** — receives a unique invite link, submits and changes RSVP responses (Yes/No/Maybe). Authenticated via unique invitee token.
- **System** — the backend (Spring Boot + PostgreSQL); enforces capacity, manages waitlist, locks RSVPs at event start.

### Workflows

#### User-Facing Flows

**Create Event**
- **Trigger:** Host submits event creation form (title, date/time, capacity, location).
- **Steps:** Validate input → generate host token → persist Event row → return host dashboard URL.
- **State changes:** New Event created in `open` state; new host token issued.
- **Dependencies:** Token generator; Event repository; transactional write.

**Add Invitees**
- **Trigger:** Host submits one or more invitee emails on an existing event.
- **Steps:** Authenticate host token → validate event is `open` → for each email, generate invitee token → persist Invitation rows → return invite links.
- **State changes:** New Invitation rows created with `no-response` status; new invitee tokens issued.
- **Dependencies:** Host token resolver; Token generator; Invitation repository; duplicate-email policy (open question).

**View Host Dashboard**
- **Trigger:** Host opens dashboard URL with host token.
- **Steps:** Authenticate host token → load event → aggregate counts (Yes/No/Maybe/Waitlist/No-response) → return event state and attendee list.
- **State changes:** None (read-only).
- **Dependencies:** Host token resolver; aggregate query over RSVP and Waitlist; freshness mechanism.

**Cancel Event**
- **Trigger:** Host invokes cancel action.
- **Steps:** Authenticate host token → validate event not already canceled or ended → transition Event to `canceled` → handle outstanding waitlist entries per policy.
- **State changes:** Event: `open|closed` → `canceled`; subsequent RSVP submissions rejected.
- **Dependencies:** Host token resolver; Event state machine; waitlist cleanup policy (open question).

**Close Event to Further RSVPs**
- **Trigger:** Host invokes close action before event start.
- **Steps:** Authenticate host token → validate event is `open` → transition Event to `closed`.
- **State changes:** Event: `open` → `closed`; existing RSVPs preserved, no new submissions or changes accepted.
- **Dependencies:** Host token resolver; Event state machine.

**Open Invite Link**
- **Trigger:** Invitee opens unique invite URL.
- **Steps:** Resolve invitee token → load event and invitation → return event details and current RSVP status (including waitlist position if applicable).
- **State changes:** None (read-only).
- **Dependencies:** Invitee token resolver; Event/Invitation/RSVP repositories.

**Submit Initial RSVP**
- **Trigger:** Invitee selects Yes/No/Maybe on invite page.
- **Steps:** Authenticate invitee token → validate event is `open` and not started → within one transaction: lock capacity row, count current confirmed, decide Confirmed-Yes vs Waitlist vs No vs Maybe, persist RSVP, update aggregates → return outcome.
- **State changes:** Invitation: `no-response` → `yes(confirmed)` | `yes(waitlist)` | `no` | `maybe`; confirmed count or waitlist tail updated.
- **Dependencies:** Invitee token resolver; pessimistic lock or row-level capacity constraint; Event state check; RSVP repository.

**Change RSVP**
- **Trigger:** Invitee resubmits a different response before event start.
- **Steps:** Authenticate invitee token → validate event is `open` and not started → within one transaction: read prior state, transition (handling Confirmed-Yes → No/Maybe by triggering waitlist promotion, or Waitlist → Confirmed if capacity now allows), persist new RSVP → return outcome.
- **State changes:** Invitation status transitions per state machine; if a confirmed slot is freed, FIFO-head waitlist entry promoted to confirmed in same transaction.
- **Dependencies:** Invitee token resolver; pessimistic lock; Waitlist FIFO ordering; transactional consistency.

#### Internal System Flows

**Token Issuance**
- **Trigger:** Event creation (host token); invitee added (invitee token).
- **Steps:** Generate cryptographically strong random token → ensure uniqueness → persist with owning entity.
- **State changes:** New token row associated with host or invitee.
- **Dependencies:** Secure RNG; uniqueness constraint in DB.

**RSVP State Transition**
- **Trigger:** Invitee submit/change RSVP request.
- **Steps:** Open transaction → acquire capacity lock → load Event + Invitation + current RSVP → validate event state and timing → compute target state → write RSVP → trigger waitlist promotion if needed → commit.
- **State changes:** Invitation/RSVP row updated; possibly one Waitlist entry promoted.
- **Dependencies:** Database transaction; locking strategy; Event state; Waitlist ordering.

**Capacity Enforcement**
- **Trigger:** Any RSVP transition that would create a new Confirmed-Yes.
- **Steps:** Inside RSVP transaction → take pessimistic lock on Event capacity row (or rely on a unique constraint over confirmed slots) → count current confirmed → if `< capacity`, mark Confirmed; else append to Waitlist.
- **State changes:** Either confirmed count incremented or waitlist tail extended.
- **Dependencies:** PostgreSQL row-level locks (`SELECT ... FOR UPDATE`) or constraint-based slot table; serializable behavior for the section.

**Waitlist Promotion**
- **Trigger:** A Confirmed-Yes invitation becomes non-confirmed (No / Maybe / removed) within an RSVP transaction.
- **Steps:** Within the same transaction → query FIFO head of waitlist for this event → if present, mark that entry as Confirmed-Yes and remove from waitlist → commit.
- **State changes:** One Waitlist entry: `waitlisted` → `confirmed`; waitlist length decremented; confirmed count net unchanged.
- **Dependencies:** Same transaction as the RSVP transition; FIFO ordering (timestamp or sequence column); waitlist repository.

**Event State Management**
- **Trigger:** Host actions (close, cancel) or system trigger (event start time reached).
- **Steps:** Validate current state allows the transition → write new state → reject incoming RSVP requests according to new state.
- **State changes:** Event: `open` → `closed` | `canceled` | `locked`.
- **Dependencies:** Event state machine; scheduler (for time-based lock); Event repository.

**Authorization**
- **Trigger:** Every request to host or invitee endpoint.
- **Steps:** Extract token → look up token row → resolve to host or invitee → confirm token type matches endpoint → confirm associated event still permits the action.
- **State changes:** None.
- **Dependencies:** Token store; token-type discrimination; Event state read.

#### Background Flows

**RSVP Lock at Event Start**
- **Trigger:** Event start time reached (scheduled job, or lazy check on each RSVP write).
- **Steps:** Identify events whose `start_time <= now` and state is `open`/`closed` → transition them to `locked` → reject any subsequent RSVP submission/change.
- **State changes:** Event: `open|closed` → `locked`; RSVPs become immutable.
- **Dependencies:** Scheduler (cron / Spring `@Scheduled`) or read-time enforcement; Event state machine; consistent server clock.

**Dashboard Freshness**
- **Trigger:** Host dashboard open, or any RSVP state change.
- **Steps:** On change, recompute aggregates (or rely on transactional counts) → push to host dashboard via short-poll, SSE, or WebSocket.
- **State changes:** None in domain state; UI cache updated.
- **Dependencies:** Aggregate query or push channel; transactional consistency for counts.

#### Failure Flows

**Concurrent Yes Race (capacity overshoot)**
- **Trigger:** Two or more Yes submissions arrive simultaneously near capacity boundary.
- **Steps:** Each transaction attempts to acquire capacity lock → only one at a time proceeds with the count check → late arrivals see updated count and either confirm remaining slot or go to waitlist.
- **State changes:** Confirmed count never exceeds capacity; surplus Yes responses become waitlist entries.
- **Dependencies:** Pessimistic locking or serializable isolation; correctness of capacity check inside the lock.

**Waitlist Promotion Mid-Transaction Failure**
- **Trigger:** Exception during RSVP transition that includes a promotion.
- **Steps:** Transaction rolls back → original RSVP change is undone → no promotion occurs → error returned to caller.
- **State changes:** None committed.
- **Dependencies:** Atomic transactional boundary covering both RSVP write and promotion.

**RSVP at Event-Start Boundary**
- **Trigger:** RSVP request arrives at or just after `start_time`.
- **Steps:** RSVP transaction reads current event state and `now` against `start_time` → if `now >= start_time`, reject; else proceed (a concurrent lock job may transition event during processing).
- **State changes:** None on rejection; otherwise normal RSVP transition.
- **Dependencies:** Single source of truth for time comparison; deterministic boundary semantics (open question: inclusive vs exclusive).

**Duplicate Invitation**
- **Trigger:** Host adds an email already invited to the same event.
- **Steps:** Detect duplicate via uniqueness constraint on (event_id, email) → reject or dedupe per chosen policy.
- **State changes:** Either no new Invitation row, or new row created if policy permits.
- **Dependencies:** DB uniqueness constraint; policy decision (open question).

**Invalid / Mismatched Token**
- **Trigger:** Request with unknown, malformed, expired, or wrong-type token.
- **Steps:** Token resolver fails or returns wrong type → return 401/403.
- **State changes:** None.
- **Dependencies:** Token store; token-type discrimination.

**RSVP on Canceled / Closed / Locked Event**
- **Trigger:** Invitee submits or changes RSVP on event no longer accepting responses.
- **Steps:** Load event state → reject with reason matching the state (`canceled`, `closed`, `locked`).
- **State changes:** None.
- **Dependencies:** Event state read inside RSVP transaction.

**Waitlisted Invitee Changes to No**
- **Trigger:** Invitee currently waitlisted submits No.
- **Steps:** Within RSVP transaction → remove or retain in waitlist per chosen policy → write No state.
- **State changes:** Invitation: `yes(waitlist)` → `no`; waitlist length decremented if removed.
- **Dependencies:** Policy decision (open question); waitlist repository.

**Confirmed → No With Empty Waitlist**
- **Trigger:** Confirmed-Yes invitee submits No when no one is waitlisted.
- **Steps:** Within RSVP transaction → write No → check waitlist (empty) → no promotion → commit.
- **State changes:** Confirmed count decremented; waitlist unchanged.
- **Dependencies:** Waitlist repository read.

**Event Canceled With Active Waitlist**
- **Trigger:** Host cancels an event that has waitlist entries.
- **Steps:** Within cancel transaction → mark event canceled → resolve waitlist entries per chosen policy (leave dormant vs purge).
- **State changes:** Event: → `canceled`; waitlist entries either preserved (read-only) or removed.
- **Dependencies:** Policy decision (open question); Waitlist repository.

## Invariants

### Business

**Capacity is never exceeded.**
- *Break:* Two concurrent Yes submissions both confirm and overshoot capacity.
- *Trigger:* Concurrent RSVP writes near the capacity boundary.
- *Protection note:* Candidate — pessimistic lock on event capacity row inside the RSVP transaction; DB-level backstop (check constraint or partial unique index over confirmed slots). Not yet decided.

**Waitlist exists only at capacity.**
- *Break:* A confirmed slot frees up but the FIFO head is not promoted, leaving free capacity beside a non-empty waitlist.
- *Trigger:* Confirmed → No/Maybe transition where promotion is skipped or runs in a separate transaction that fails.
- *Protection note:* Promotion must run in the same transaction as the freeing change; periodic audit query as backstop.

**Waitlist is FIFO.**
- *Break:* Two waitlist entries promoted out of order.
- *Trigger:* Promotion query without deterministic ordering, or tied timestamps with no tie-breaker.
- *Protection note:* Per-event monotonic `waitlist_sequence` column; promotion always orders by it ascending.

**One RSVP per invitation.**
- *Break:* Two RSVP rows exist for the same invitation.
- *Trigger:* Concurrent first-time submissions on an insert-only RSVP table.
- *Protection note:* Unique constraint on `rsvp.invitation_id`, or model RSVP as a column on Invitation; transactional upsert with the invitation row locked.

**RSVPs are immutable after event start.**
- *Break:* An RSVP write commits at or after `start_time`.
- *Trigger:* Late submission at the start-time boundary; transaction that started before `start_time` and commits after.
- *Protection note:* **Currently unprotected — no mechanism, clock source, or boundary semantics decided.** Candidates: scheduled job flipping event to `locked`, lazy `now` vs `start_time` check on every RSVP write, or both.

**No RSVPs on non-open events.**
- *Break:* RSVP accepted on a `closed`, `canceled`, or `locked` event.
- *Trigger:* RSVP path that does not re-read event state inside the transaction.
- *Protection note:* Re-read event state inside the RSVP transaction (after lock acquisition); reject if not `open`.

**Terminal event states are terminal.**
- *Break:* `canceled` event transitions back to `open`.
- *Trigger:* Generic state setter, admin tooling bypassing the state machine.
- *Protection note:* Explicit allowed-transition map in code; DB check constraint or trigger.

### Data Integrity

**Email uniqueness within an event.**
- *Break:* Same email invited twice (or as `Alice@x` and `alice@x`) to the same event.
- *Trigger:* Concurrent "add invitee" requests; case-variant duplicates.
- *Protection note:* Unique index on `(event_id, lower(email))`. Duplicate-policy decision still open.

**Token uniqueness globally.**
- *Break:* Two principals resolved by the same token.
- *Trigger:* Weak generator, no uniqueness check on insert.
- *Protection note:* ≥128-bit cryptographic random; unique index on token value; retry on collision.

**Capacity is positive.**
- *Break:* Event created with `capacity ≤ 0`.
- *Trigger:* Missing server-side validation on the create form.
- *Protection note:* DB check constraint `capacity ≥ 1`; server-side validator.

**Event start time is set and immutable.**
- *Break:* `start_time` becomes null or changes after creation.
- *Trigger:* Update endpoint that exposes the field.
- *Protection note:* `NOT NULL`; no update path for `start_time`; trigger rejecting UPDATE if needed.

**Counts match underlying rows.**
- *Break:* Denormalized counter drifts from row count.
- *Trigger:* Counter updated outside the transaction that changed the rows; failure path that misses a decrement.
- *Protection note:* Prefer aggregating from rows on read; if denormalized, update inside the same transaction plus a reconciliation job.

### Authorization

**Token type matches endpoint.**
- *Break:* Invitee token used on a host endpoint (or vice versa).
- *Trigger:* Auth layer that checks validity but not type.
- *Protection note:* Explicit `type` field on tokens; auth middleware checks both validity and type per route.

**Host scope is bound to one event.**
- *Break:* Host token used to read or mutate a different event.
- *Trigger:* Endpoint that trusts a client-supplied `event_id`.
- *Protection note:* Derive event from the host token; reject if the request specifies a different event.

**Invitee scope is bound to one invitation.**
- *Break:* Invitee token used to read or change another invitee's RSVP.
- *Trigger:* Endpoint that accepts `invitation_id` from the client.
- *Protection note:* Derive invitation from the token; never accept invitation id from the request.

**Token resolution precedes state checks.**
- *Break:* Unauthorized caller learns event existence/state from error messages.
- *Trigger:* Code returning state-specific errors before auth runs.
- *Protection note:* Auth middleware runs first; uniform 404/403 for unauthorized callers.

**Token confidentiality.**
- *Break:* Token leaks via logs, error messages, referers, or analytics.
- *Trigger:* Logging tokens; including tokens in query strings; verbose error responses.
- *Protection note:* Log-redaction filters; HTTPS only; tokens carried in headers or scoped path segments.

### Concurrency

**Capacity check and confirm are atomic.**
- *Break:* Two transactions both observe a free slot and both confirm.
- *Trigger:* Read-then-write across separate transactions; insufficient isolation.
- *Protection note:* `SELECT ... FOR UPDATE` on event capacity row at the start of the RSVP transaction.

**Promotion is atomic with the freeing transition.**
- *Break:* Confirmed slot freed but promotion never commits.
- *Trigger:* Promotion implemented as async or separate transaction.
- *Protection note:* Promotion is a synchronous step inside the same `@Transactional` boundary.

**No lost updates on RSVP changes.**
- *Break:* Two concurrent change requests on the same invitation overwrite each other's bookkeeping.
- *Trigger:* Two writes to the same invitation race.
- *Protection note:* Lock the invitation row at transition start, or optimistic-lock with a version column and retry.

**Event state transitions are serialized.**
- *Break:* Concurrent close/cancel/lock produce inconsistent state.
- *Trigger:* Two host requests, or host vs scheduler, racing on the same event.
- *Protection note:* Lock event row before any state transition; state machine rejects illegal transitions.

**Time-based lock is monotonic.**
- *Break:* RSVP commits after `start_time` because the transaction started earlier.
- *Trigger:* Long-running RSVP transaction crossing the boundary; clock skew between scheduler and request handlers.
- *Protection note:* Compare DB `now()` to `start_time` after acquiring the event lock; single authoritative clock source.

**No phantom waitlist promotions.**
- *Break:* Same waitlist entry promoted twice.
- *Trigger:* Promotion query without locking the chosen row.
- *Protection note:* `SELECT ... FOR UPDATE SKIP LOCKED ... LIMIT 1` for FIFO head; update/delete in same transaction.

### Tenant Isolation (per-event scope)

**Cross-event invitation isolation.**
- *Break:* An invitation associated with multiple events.
- *Trigger:* Schema permitting multi-event linkage; bulk-import code linking across events.
- *Protection note:* Single non-null `invitation.event_id`; no join table.

**Cross-event token isolation.**
- *Break:* Token issued for event A grants access to event B.
- *Trigger:* Token reuse across events; resolver returning principal without event scope.
- *Protection note:* Token bound to a single event at issuance; auth returns `(principal, event_scope)` and downstream uses that.

**Cross-event waitlist / count isolation.**
- *Break:* Promotion or count query touches another event's data.
- *Trigger:* Query missing `WHERE event_id = ?`; counter keyed only by status.
- *Protection note:* All waitlist and aggregate queries scoped by `event_id`; counters keyed by `(event_id, status)`.

**Host-to-host isolation.**
- *Break:* Host reads or mutates another host's event.
- *Trigger:* Endpoint trusting client-supplied `event_id`.
- *Protection note:* Derive event from host token; assert any path-supplied id matches.

**Invitee-to-invitee isolation.**
- *Break:* Invitee sees other invitees' identities or RSVPs beyond product-decided aggregates.
- *Trigger:* Endpoint returning the full attendee list to invitees; error messages disclosing membership.
- *Protection note:* Invitee endpoints return only the caller's invitation/RSVP; aggregates stripped of identifying detail.

**No cross-event data leakage in errors.**
- *Break:* Error messages reveal event existence or state to unauthorized callers.
- *Trigger:* Distinct error codes for "not found" vs "not yours" vs "canceled".
- *Protection note:* Uniform 404/403 for unauthorized access; detailed reasons only in server-side logs.

## Proposed Architecture

**API Layer**
- *Responsibility:* HTTP boundary — request validation, response shaping; no business logic.
- *Inputs:* HTTP requests carrying a token (host or invitee).
- *Outputs:* HTTP responses (JSON); delegated calls to services.
- *Ownership:* Owns transport concerns only; never opens transactions.
- *Notes:* Two endpoint groups — host and invitee — distinguished by token type.

**Auth Resolver**
- *Responsibility:* Resolve token → `(principal_type, event_scope, invitation_scope?)`; enforce token-type-to-endpoint match before service code runs.
- *Inputs:* Raw token from request.
- *Outputs:* Resolved principal context, or rejection (401/403).
- *Ownership:* Owns the auth boundary; runs on every request.
- *Notes:* Likely a Spring filter/interceptor. Uniform rejection responses to avoid leaking event existence.

**Event Service**
- *Responsibility:* Event lifecycle (create, close, cancel, lock); invitation membership (add invitee); inline token issuance at creation and invite time.
- *Inputs:* Host commands from the API layer (after auth).
- *Outputs:* Persisted Event/Invitation rows; issued tokens; event state transitions.
- *Ownership:* Owns the Event aggregate and its Invitation children.
- *Notes:* Token generation is inline, not a separate component. State machine enforces allowed transitions.

**RSVP Service**
- *Responsibility:* Single transactional path for RSVP transitions — read prior state, capacity check, write RSVP, promote FIFO waitlist head if a confirmed slot frees.
- *Inputs:* Invitee submit/change requests (after auth).
- *Outputs:* Updated RSVP state; possible waitlist promotion; outcome returned to caller.
- *Ownership:* Sole owner of capacity bookkeeping and waitlist promotion. No other component reads or writes confirmed counts or waitlist entries.
- *Notes:* Architecturally load-bearing. The only place the capacity invariant is enforced. Mechanism for atomic enforcement (lock vs constraint) is an open design choice.

**Database (PostgreSQL)**
- *Responsibility:* Source of truth for all state; atomic enforcement of capacity per the stated constraint.
- *Inputs:* SQL from JPA repositories within service transactions.
- *Outputs:* Persisted state; lock contention; constraint violations.
- *Ownership:* Owns durability and the atomic-enforcement primitives (row locks and/or constraints).
- *Notes:* Not just storage — an active enforcement participant. Specific locking strategy is an open design choice.

### Interaction Summary

Every request enters through the **API layer**, is resolved by the **Auth resolver** into a scoped principal, and is then routed to either the **Event service** (host actions: create, add invitees, close, cancel) or the **RSVP service** (invitee actions: submit, change). Both services persist through the **Database**, which carries enforcement responsibility for capacity and uniqueness — not just storage.

The RSVP service is the only component whose work crosses multiple invariants in one step: it reads prior state, checks capacity under a lock, writes the new RSVP, and promotes the FIFO waitlist head when a confirmed slot frees — all within a single transaction. The host dashboard reads aggregate state from the Database without coupling to that write path. Two architectural placeholders remain unresolved: the mechanism that locks RSVPs at event start, and the mechanism that keeps the host dashboard fresh without polling delays.

## Data Ownership and State Model

**Event**
- *Source of truth:* `event` row in PostgreSQL.
- *Mutated by:* Event service at creation; Event service on host actions (close, cancel); scheduler on `locked` transition (only if the scheduler model is chosen — open).
- *Read by:* Auth resolver, Event service, RSVP service (inside its transaction), host dashboard.
- *Derived state:* "is locked" is derived from `now ≥ start_time` if the lazy model is chosen; persisted on `event.state` if the scheduler model is chosen.
- *Lifecycle:* `open` → `closed` | `canceled` | `locked`. Identity fields (date, capacity, location) immutable after creation. `canceled` is terminal.

**Invitation**
- *Source of truth:* `invitation` row, scoped by `event_id`.
- *Mutated by:* Event service at creation only.
- *Read by:* Invitee surface (own invitation), host dashboard (full list), Auth resolver (to resolve invitee token).
- *Derived state:* none.
- *Lifecycle:* created when host adds invitee; otherwise immutable in identity. Embedded RSVP state changes separately.

**RSVP State (per invitation)**
- *Source of truth:* status column on Invitation (proposed) — `no-response | yes | no | maybe`.
- *Mutated by:* RSVP service only, inside its single transactional path.
- *Read by:* Invitee surface (own value), host dashboard (per-invitation list and aggregates).
- *Derived state:* the Confirmed-vs-Waitlisted classification of a Yes is derived from waitlist membership, not stored as a fourth value.
- *Lifecycle:* `no-response` until first submission; freely transitions among `yes / no / maybe` until event start; immutable after lock.

**Waitlist Membership and Order**
- *Source of truth:* waitlist rows (or status + sequence column on Invitation — open). Per-event monotonic sequence carries FIFO order.
- *Mutated by:* RSVP service only, in the same transaction as the RSVP write that caused the change.
- *Read by:* RSVP service (FIFO head on promotion); host dashboard (length); invitee surface (own position, if product chooses to expose).
- *Derived state:* waitlist length and a specific invitee's position are derived from rows.
- *Lifecycle:* entry created when a Yes arrives at full capacity; removed on promotion to Confirmed, or on RSVP change away from Yes (policy open); cleanup on event cancel is policy-dependent (open).

**Tokens (host and invitee)**
- *Source of truth:* `token` row, bound at issuance to an event (host) or invitation (invitee), with a type discriminator.
- *Mutated by:* Event service at issuance only (host token at event creation, invitee token at invitee add). No further mutation.
- *Read by:* Auth resolver only.
- *Derived state:* validity (active vs expired) is derived from `expires_at` if expiry is adopted (open question).
- *Lifecycle:* created with owning entity; valid until event ends, or until expiry if introduced. No revocation path defined.

**Aggregates and Counts**
- *Source of truth:* underlying invitation, RSVP, and waitlist rows. No persisted counters.
- *Mutated by:* no one — fully derived.
- *Read by:* host dashboard.
- *Derived state:* all of it (Yes / No / Maybe / Confirmed / Waitlist / no-response counts).
- *Lifecycle:* recomputed per read. Denormalization is explicitly not part of the initial model; introducing it later requires naming a sole writer.

## Concurrency and Correctness Notes

**RSVP submit / change**
- *Workflow / state:* Invitee submits or changes RSVP; mutates RSVP state, capacity bookkeeping, possibly waitlist.
- *Risk:* duplicate writes, lost updates, capacity overshoot.
- *What can go wrong:* double-submit from two tabs or a client retry creates two RSVP rows; two concurrent changes on the same invitation interleave bookkeeping; two Yes submissions both observe a free slot and both confirm.
- *Control note:* one DB transaction covering the full path; pessimistic lock on the event row before reading capacity; absolute-set semantics (`PUT yes`) so retries are idempotent; unique constraint on `rsvp.invitation_id` (or status modeled as a column on Invitation).

**Waitlist promotion**
- *Workflow / state:* When a Confirmed-Yes flips to non-confirmed, the FIFO head is promoted.
- *Risk:* duplicate promotion, skipped promotion, out-of-order promotion.
- *What can go wrong:* two freeing transactions both pick the same head; promotion runs in a separate transaction that fails, leaving a free slot beside a non-empty waitlist; ties in ordering produce non-FIFO promotion.
- *Control note:* promotion lives in the same transaction as the freeing write; `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` to claim the head; per-event monotonic sequence column for unambiguous FIFO.

**Capacity enforcement**
- *Workflow / state:* Decision to mark a Yes as Confirmed vs Waitlist.
- *Risk:* total confirmed exceeds capacity.
- *What can go wrong:* read-then-write across separate transactions; capacity read outside a lock; service-layer bug bypasses the check.
- *Control note:* pessimistic lock on the event row inside the RSVP transaction; DB-level backstop (check constraint or partial unique index over confirmed slots) so service correctness is not the only line of defense.

**Event state transitions (close, cancel, lock)**
- *Workflow / state:* `event.state` transitions among `open`, `closed`, `canceled`, `locked`.
- *Risk:* conflicting updates, illegal transitions, unclear writer authority.
- *What can go wrong:* host close races with scheduler lock; cancel races with an in-flight RSVP; retry of a transition reapplies it; two writers (host + scheduler) both target `locked` with no rule for who wins.
- *Control note:* row lock on the event before any state transition; explicit state machine where illegal transitions are no-ops (so retries are safe); name a single writer for the `locked` transition — open question (scheduler vs lazy-derived).

**RSVP lock at event start**
- *Workflow / state:* Boundary at `event.start_time` after which RSVPs are immutable.
- *Risk:* late RSVPs accepted; stale "is locked" reads; clock disagreement.
- *What can go wrong:* RSVP transaction begins before `start_time` and commits after; scheduler runs late and lock state lags; two app servers compute `now ≥ start_time` differently.
- *Control note:* boundary check using DB `now()` *inside* the RSVP transaction after the event lock is held — this is the safety net regardless of scheduler timing. Pick boundary semantics (`>` vs `≥`) and apply consistently. Currently this invariant is unprotected — it's the design's most exposed area.

**Add invitee (duplicates)**
- *Workflow / state:* Host adds invitee email to event.
- *Risk:* duplicate invitations.
- *What can go wrong:* two concurrent adds for the same email; case-variant duplicates (`Alice@x` vs `alice@x`); same retry submitted twice.
- *Control note:* unique constraint on `(event_id, lower(email))`. Reject vs dedupe behavior is an open product question; the constraint is the same either way.

**Token issuance**
- *Workflow / state:* Generate host token at event creation; invitee tokens at invitee add.
- *Risk:* collision; predictable tokens.
- *What can go wrong:* weak generator produces guessable values; collision silently overwrites another principal's token.
- *Control note:* cryptographic random ≥128 bits; unique constraint on token value with retry on collision; tokens never logged or echoed in error responses.

**Authorization**
- *Workflow / state:* Every request resolves a token before reaching service code.
- *Risk:* cross-scope access; stale-read on event state at auth time.
- *What can go wrong:* invitee token used on host endpoint (or vice versa); endpoint trusts a client-supplied `event_id`; event state read at auth changes before the service runs.
- *Control note:* token resolver returns `(principal, event_scope, invitation_scope?)` derived from the token itself; service write paths re-read event state under their own lock — auth-time snapshot is never the basis for a write decision.

**Dashboard reads**
- *Workflow / state:* Host reads aggregate counts and attendee list.
- *Risk:* stale counts; cache drift if ever denormalized.
- *What can go wrong:* counts lag a just-committed RSVP; future caching layer drifts from row truth on a missed invalidation.
- *Control note:* recompute on read from rows — no caching layer in v1. If denormalized counters are added later, RSVP service is the sole writer and updates inside the same transaction as the row change.

**Worker retries (scheduler today; future async)**
- *Workflow / state:* Scheduled lock job; future async work if added.
- *Risk:* at-least-once delivery double-applies effects; out-of-order execution.
- *What can go wrong:* same lock job runs twice for the same event; a future async hop fires after the underlying state has already moved on.
- *Control note:* every job's effect is idempotent (state-machine no-op on repeat). Queue serialization is not needed today; if async work is later introduced, the queue must be keyed by event id *and* consumers must remain idempotent — neither alone is sufficient.

**Side effects**
- *Workflow / state:* Logging; future emails; the immutability assumption itself.
- *Risk:* token leakage; transactional coupling to external systems; silent invariant violation if assumptions relax.
- *What can go wrong:* tokens appear in logs or error responses; an inline email send couples mail-server uptime to RSVP correctness; event date or capacity becomes editable and invalidates every capacity/waitlist invariant mid-flight.
- *Control note:* log-redaction filters; if email is added later, use a transactional outbox written inside the RSVP transaction and delivered by an idempotent consumer — never send inline. Treat event date/capacity immutability as load-bearing, not stylistic.

*Note:* optimistic version checks are a viable alternative to pessimistic locks on the invitation row, but wherever capacity is involved, locking is the stronger fit because the contended resource is the shared slot, not a single row.

## Risks and Failure Notes

**Start-time lock currently unprotected.**
- *Failure shape:* RSVPs accepted at or after `start_time`; "RSVPs immutable after start" invariant violated.
- *Cause:* No mechanism, clock source, or boundary semantics chosen. Open question.
- *Note:* The single most exposed area in the design. Resolve before any other scaling concern.

**Capacity overshoot under concurrent confirms.**
- *Failure shape:* `confirmed_count > capacity`; overbooking.
- *Cause:* Capacity check and confirm not held in the same atomic critical section (no lock, or lock at the wrong granularity).
- *Note:* Confirmed fact mandates atomic enforcement; the *mechanism* is undecided. Pessimistic lock + DB constraint backstop is the candidate.

**Waitlist promotion outside the freeing transaction.**
- *Failure shape:* free slot beside non-empty waitlist; or same FIFO head promoted twice.
- *Cause:* Promotion implemented as async, separate transaction, or unlocked SELECT.
- *Note:* Promotion must be synchronous and inside the freeing transaction. Non-negotiable.

**Multi-writer authority over `event.state`.**
- *Failure shape:* host close races scheduler lock; final state depends on commit order.
- *Cause:* No single writer named for the `locked` transition; scheduler model and host actions both target the same column.
- *Note:* Pick one writer per transition, or drop the persisted `locked` state entirely (lazy model).

**Cancel cleanup mutating waitlist.**
- *Failure shape:* waitlist data written by Event service even though RSVP service is supposed to be the sole owner.
- *Cause:* Cancel-with-active-waitlist policy unresolved.
- *Note:* Either delegate cleanup to the RSVP service or formalize that on cancel both services may write. Do not leave authority ambiguous.

**Token leakage.**
- *Failure shape:* long-lived auth bypass; an attacker holding a leaked token has full scope of the original principal until the event ends.
- *Cause:* Tokens in logs, error responses, query strings, browser history, referers.
- *Note:* Log-redaction filters; tokens carried in headers or scoped path segments; HTTPS only; no token in query strings.

**One hot event saturates shared infrastructure.**
- *Failure shape:* RSVPs and dashboard reads for unrelated events slow or fail because one event consumes the connection pool, lock manager, or DB CPU.
- *Cause:* No per-event resource isolation, no per-event rate limit, single shared DB and pool.
- *Note:* Tolerable under the implicit "events take turns being busy" assumption. Per-event rate limit + bounded SSE connections needed if that assumption breaks.

**Multiple simultaneous hot events.**
- *Failure shape:* aggregate latency degradation across the system even though no single event exceeds its own ceiling.
- *Cause:* Shared connection pool and DB CPU; the architecture has no model for concurrent contention across events.
- *Note:* The scale problem the design quietly assumes is far away. No fix needed today; recognize the signal when it appears.

**Per-event RSVP throughput ceiling (~50–200/sec).**
- *Failure shape:* lock contention queues RSVP transactions on a hot event; user-visible latency or timeouts.
- *Cause:* Pessimistic lock on a single event row serializes all RSVP writes for that event.
- *Note:* Hard ceiling that no JVM/DB sizing fixes. Replacement (constraint-based slot table) is a real architectural change, not a tune.

**Dashboard freshness mechanism not chosen.**
- *Failure shape:* host dashboard shows stale counts, or push fan-out exhausts the connection pool.
- *Cause:* Constraint says "without polling delays" but mechanism is unspecified.
- *Note:* Pick polling, SSE, or `LISTEN/NOTIFY` deliberately. Each has a different blast radius for noisy-neighbor risk.

**Token revocation gap.**
- *Failure shape:* a leaked or stolen token cannot be invalidated before the event ends.
- *Cause:* Token expiry/revocation is an open question; no path defined.
- *Note:* Acceptable if tokens are treated as event-scoped capabilities with a known expiry (event end). Revisit if expiry is added later — DB-backed tokens already support revocation; do not move to signed/JWT-style without keeping a revocation story.

**Duplicate-email policy unresolved.**
- *Failure shape:* either two invitations for the same person (confusing, double-counted), or surprise rejection of a deliberate re-invite.
- *Cause:* Open product question.
- *Note:* DB constraint is the same either way (`unique(event_id, lower(email))`); only the response behavior differs.

**Cross-event leakage via missing scope filter.**
- *Failure shape:* one event's data appears in another event's query result; counts contaminated.
- *Cause:* A query that omits `WHERE event_id = ?` or a counter keyed only by status.
- *Note:* The DB has no row-level security here — defense is code review and tests. Worth a static check or test that asserts every query against tenant tables includes `event_id`.

**Editability assumption broken in the future.**
- *Failure shape:* every capacity check, confirmed count, and waitlist promotion already in flight becomes incoherent.
- *Cause:* Adding edit support to event date or capacity after the fact.
- *Note:* Immutability is load-bearing for correctness, not just simplicity. Any future "edit" feature is an architectural change, not a feature addition.

**Boundary semantics at `start_time`.**
- *Failure shape:* `now == start_time` accepted in one path and rejected in another; nondeterministic acceptance near the boundary.
- *Cause:* Inclusive vs exclusive choice not documented; no single authoritative clock source.
- *Note:* Pick `>` or `≥` once; use DB `now()` everywhere; apply the same semantics in lazy and scheduler paths.

**Synchronous-everything fragility.**
- *Failure shape:* a slow query, a long lock wait, or a saturated pool surfaces as user-facing latency or 5xx with no shock absorber.
- *Cause:* No queue, no outbox, no async backpressure anywhere in the system.
- *Note:* Deliberate for v1 — keeps invariants enforceable. Becomes a real concern only when paired with multiple simultaneous hot events.

## Alternatives Considered

**Constraint-based slot table.**
Replace the event-row lock with a `confirmed_slot(event_id, slot_ordinal)` table, unique on `(event_id, slot_ordinal)`. Confirm = INSERT, decline = DELETE, promotion = INSERT into freed ordinal. Pros: DB-structural capacity enforcement, materially higher per-event throughput. Cons: ordinal allocation and retry-on-conflict logic; harder to support mid-event capacity edits. *When to switch:* measured per-event contention.

**Postgres serializable isolation.**
Run the RSVP transaction at `SERIALIZABLE` and let SSI detect conflicts. Pros: cleanest mental model — code reads as if single-threaded. Cons: every transaction needs a retry loop; serialization failures appear only under load; debugging is the hardest of the three under stress. Considered, not chosen — the operational tax outweighs the readability win at this scope.

**Advisory lock per event (`pg_advisory_xact_lock(event_id)`).**
Variant of the chosen design — same semantics as the row lock, expressed as a named per-event lock instead of a row lock. Operationally near-identical; reasonable substitute if the team prefers it stylistically.

## Tradeoffs (explicit)

- Per-event RSVP throughput is **capped at ~50–200 writes/sec** by the event-row lock. Sufficient for the stated scope, not for webinar-scale events.
- **Synchronous-everything**: no queue, no outbox, no async fan-out. Simpler invariants, no shock absorber under load.
- **No resource isolation between events**: one hot event can saturate the shared DB and connection pool. Acceptable under the implicit "events take turns being busy" assumption.
- **Aggregate-on-read for dashboards**: avoids the "counts match rows" invariant, costs one aggregate query per refresh. Cheap at small capacity, gets expensive with push freshness on large events.
- **DB-backed tokens**: keeps revocation possible, costs one indexed lookup per request. Trades a small per-request tax for not committing to JWT-style tokens.
- **Immutable events** (no edits to date, capacity, location): load-bearing for correctness, not just simplicity. Any future "edit" feature is an architectural change, not a feature.
- **Tenant boundary is the event** (no organization, no host accounts spanning events): simplifies auth and scoping today; a hard reshape if multi-event hosts are ever required.
- **Lock for `locked` transition undecided**: scheduler vs lazy-derived is a real choice deferred to keep v1 small. The lazy boundary check inside RSVP writes is the safety net regardless of which is picked.

## Rollout / Migration Notes

This is a greenfield build — no existing data to migrate. Sequencing is about which decisions become irreversible once the first real event exists.

**Build sequence**
1. Schema first: `event`, `invitation`, `token` tables with their final unique constraints (`token` value, `(event_id, lower(email))`).
2. Capacity-enforcement mechanism (row lock + DB constraint backstop) — must be in place before the RSVP endpoint is exposed.
3. Start-time lock mechanism — must be resolved before opening the system to real RSVPs (currently unprotected; do not ship RSVPs without it).
4. RSVP submit/change endpoint with absolute-set semantics (`PUT yes/no/maybe`).
5. Waitlist promotion in the same transaction.
6. Host dashboard read path.
7. Cancel / close / lock state transitions.

**Decisions that become irreversible at first-event creation**
- Token length and format — existing invite links can't be retroactively widened.
- Duplicate-email policy — the unique-constraint shape is forward-only; loosening later means dropping the constraint and accepting historical drift.
- Boundary semantics at `start_time` (`>` vs `≥`) — applies to every event already scheduled.
- Event immutability — adding edit support later is a real schema/invariant change, not a feature add.

**Forward-compatible (safe to defer)**
- Denormalized counters (can be added later inside the same RSVP transaction without breaking reads).
- Push freshness mechanism (polling first, SSE/`LISTEN/NOTIFY` later).
- Per-event rate limits (additive, can be inserted at the API layer without schema change).

**One-way migrations to plan for**
- Row-lock → constraint-based slot table: requires backfilling `confirmed_slot` rows for existing confirmed RSVPs and freezing writes during the swap. Plan it as a maintenance window per affected event, not a blue/green deploy.
- Adding token expiry: requires a default `expires_at` for existing tokens (event end is the natural choice).

**Rollback concerns**
- Schema migrations are the only real rollback risk. Each migration should be additive (new columns nullable, new tables empty-on-create) so the prior service version can keep running on the new schema.
- The constraint backstop on capacity is *not* safely removable once data depends on it — treat it as one-way.

**Operationally sensitive during rollout**
- The first event with non-trivial capacity is the real load test for the lock mechanism. Do not run it cold against production traffic — exercise it with a synthetic event first.
- Tokens for the first real event are the first opportunity for log-leak regressions. Verify redaction filters with a deliberate token-shaped string in a request before opening real invites.
- The lock-trigger mechanism (scheduler or lazy) cannot be silently changed across a release that has events spanning the upgrade — pick before any event is created whose `start_time` would land mid-rollout.

**Flags worth having**
- One flag to disable RSVP writes globally (kill-switch, returns 503). Cheap, addresses the "we shipped a bug in the capacity path" scenario.
- Per-event "close" already exists as a host action — no separate flag needed.
- No need for finer-grained flags at this scope.

## Open Questions Recap

Consolidated view of unresolved decisions that surface throughout this document. Each is named where it bites; nothing here is invented.

**Correctness**
- RSVP-lock trigger at `start_time`: scheduler-flipped vs lazy-derived vs both. *Currently unprotected — most exposed area in the design.*
- Boundary semantics at `start_time`: `>` vs `≥`. Apply consistently across whichever lock model is chosen.
- Authoritative clock source: DB `now()` is the candidate, not yet decided.
- Single writer for the `event.state = locked` transition: scheduler or RSVP-write path.
- Waitlisted invitee changes to "No": removed from waitlist, or retained until promotion?
- Cancel with active waitlist: leave waitlist entries dormant, or purge? (Also affects authority — see below.)
- Cancel-cleanup authority: if cancel purges waitlist, Event service writes data RSVP service is supposed to own.
- Cancel vs close: exact behavioral difference is undefined.
- Intermediate event states (`draft`, `published`, `started`, `ended`): are these needed, or only `open / closed / canceled / locked`?
- Duplicate-email policy: reject vs dedupe. The DB unique constraint is the same either way; only the response behavior differs.

**Capacity enforcement mechanism**
- Pessimistic row-lock (proposed default), constraint-based slot table, serializable isolation, or advisory lock — all viable, see *Alternatives Considered*. The DB-level backstop (check or partial unique index) is a candidate, not chosen.
- RSVP storage shape: status column on Invitation (proposed) vs separate RSVP entity. Affects "one RSVP per invitation" enforcement.
- Waitlist representation: separate table vs status + sequence column on Invitation.

**Security**
- Token expiry: do tokens expire, and if so, when? Default candidate is "valid until event ends."
- Token revocation: if expiry is added, who flips a token to invalid?
- Host re-access path: how does a host retrieve their event link if lost? No mechanism defined.
- Token transport: header vs path segment vs query string. Query string carries log/referer leakage risk.

**Operational / freshness**
- Dashboard freshness mechanism: short polling, SSE, or `LISTEN/NOTIFY`. Constraint requires "no polling delays" but no mechanism is chosen.

**Scale signals (deferred, named here so they aren't mistaken for solved)**
- Per-event RSVP throughput beyond the row-lock ceiling (~50–200/sec) — triggers migration to slot table.
- Multiple simultaneous hot events — triggers per-event rate limits and bounded SSE connections.
- Sustained dashboard read pressure — triggers denormalized counters or read-replica routing.

These items are deferrable but not invisible: each one is referenced inline at the section where it would bite. Resolving any of them should update both that section *and* this recap.
