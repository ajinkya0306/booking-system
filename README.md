# Booking System with Race Condition Protection

Spring Boot 3 (Java 17) backend where many users can try to book the same slot at the same
time, and **a slot can be booked only once** - even under high concurrency and across
application restarts.

* Spring Web, Spring Data JPA (Hibernate), Spring Security (HTTP Basic, USER / ADMIN roles)
* H2 database (file based, so data survives restarts)
* JUnit 5 + MockMvc tests, JaCoCo coverage check (>= 80 %)

---

## 1. How to run

Requirements: JDK 17+ and Maven 3.8+.

```bash
mvn spring-boot:run          # starts on http://localhost:8080
mvn test                     # runs all tests, coverage report in target/site/jacoco/index.html
mvn verify                   # tests + fails the build if line coverage < 80 %
```

Demo users are created automatically on first start (stored in the DB, passwords BCrypt hashed):

| username | password   | role  |
|----------|------------|-------|
| admin    | admin123   | ADMIN |
| user1    | user123    | USER  |
| user2    | user123    | USER  |

## 2. API

| Method & path                       | Role         | Description                               |
|-------------------------------------|--------------|-------------------------------------------|
| `POST /slots`                       | ADMIN        | Create a slot `{startTime, endTime}`      |
| `GET /slots`                        | USER, ADMIN  | List available and booked slots           |
| `POST /bookings`                    | USER         | Book a slot `{slotId}`                    |
| `POST /bookings/{id}/cancel`        | USER         | Cancel **own** booking                    |
| `POST /admin/bookings/{id}/cancel`  | ADMIN        | Cancel any booking                        |

Status codes: `201` created, `200` ok, `400` invalid input, `401` not authenticated,
`403` not allowed, `404` not found, `409` conflict (slot already booked / already cancelled /
concurrent modification).

Error body example:

```json
{ "timestamp": "2030-01-01T10:00:00Z", "status": 409, "error": "Conflict", "message": "Slot 1 is already booked" }
```

Quick try-out:

```bash
curl -u admin:admin123 -X POST localhost:8080/slots -H 'Content-Type: application/json' \
     -d '{"startTime":"2030-06-01T10:00:00","endTime":"2030-06-01T11:00:00"}'
curl -u user1:user123 localhost:8080/slots
curl -u user1:user123 -X POST localhost:8080/bookings -H 'Content-Type: application/json' -d '{"slotId":1}'
curl -u user2:user123 -X POST localhost:8080/bookings -H 'Content-Type: application/json' -d '{"slotId":1}'   # -> 409
curl -u user1:user123 -X POST localhost:8080/bookings/1/cancel
```

## 3. Chosen locking strategy: pessimistic locking (database row lock)

The slot row is loaded with `@Lock(LockModeType.PESSIMISTIC_WRITE)`
(`SlotRepository.findByIdForUpdate`), which makes Hibernate issue `SELECT ... FOR UPDATE`.

Why pessimistic and not optimistic? Booking a popular slot is a *high-contention* operation.
With optimistic locking all competing requests would do the work and all but one would fail at
commit time and need retries. With a row lock the requests queue up, the first one wins, and
every other request reads the committed `BOOKED` state and fails immediately with a clean
`409`. It is simple, predictable and needs no retry logic.

Defence in depth - three independent database-level protections:

1. **Pessimistic row lock** on the slot (primary mechanism).
2. **`@Version` column on `Slot`** (optimistic lock) - a stale write can never overwrite newer data.
3. **`UNIQUE` constraint on `bookings.active_slot_id`** - holds the slot id while the booking is
   `ACTIVE` and `NULL` when `CANCELLED`. Even if application logic was bypassed, the database
   itself refuses a second active booking for one slot (multiple cancelled rows are allowed
   because NULLs are not equal in a unique index).

No `synchronized`, no in-memory locks, no application-side state: everything lives in the
database, so **restarting the application cannot affect correctness** (and the H2 database is
file based, so data survives restarts).

## 4. Transaction boundaries

Each public method of `BookingService` is exactly one `@Transactional` unit:

```
bookSlot(slotId, user)                      BEGIN
  1. load user                              
  2. SELECT slot ... FOR UPDATE   <-- lock acquired, others wait here
  3. if slot.status != AVAILABLE -> throw SlotAlreadyBookedException  (ROLLBACK)
  4. slot.status = BOOKED
  5. INSERT booking (ACTIVE, active_slot_id = slot id)
                                            COMMIT  <-- lock released
```

* Slot update and booking insert are in the same transaction -> they commit together or not at
  all (no partial updates, slot status always matches booking state).
* Any exception (a `RuntimeException`) rolls the whole transaction back.
* `cancelOwnBooking` / `cancelAnyBooking` use the same pattern: lock slot -> reload booking ->
  check ownership/state -> set booking `CANCELLED` + slot `AVAILABLE` -> commit.
* **Lock ordering:** both book and cancel lock the *slot first*, so two transactions can never
  wait for each other in a cycle (no deadlocks).
* In cancel, only the slot id of the booking is read before the lock (scalar query), so the
  booking itself is loaded *after* the lock and is never a stale cached copy.
* The H2 `LOCK_TIMEOUT=10000` means a waiting transaction gives up after 10 s; that case is
  turned into a `409` as well.

## 5. How race conditions are prevented (example concurrent scenario)

Two users, A and B, press "book" for slot 1 at exactly the same moment:

| time | Transaction A                          | Transaction B                              |
|------|----------------------------------------|--------------------------------------------|
| t1   | `SELECT slot 1 FOR UPDATE` -> lock OK  | `SELECT slot 1 FOR UPDATE` -> **waits**    |
| t2   | status is AVAILABLE -> set BOOKED      | (blocked)                                  |
| t3   | INSERT booking, **COMMIT**, lock freed | (blocked)                                  |
| t4   |                                        | lock acquired, reads status = **BOOKED**   |
| t5   | response `201 Created`                 | throws `SlotAlreadyBookedException` -> `409 Conflict`, ROLLBACK |

Without the lock, both transactions would read `AVAILABLE` at t1 and both would insert a
booking (the classic check-then-act race = double booking).

### Try it yourself

1. Terminal 1: `mvn spring-boot:run`
2. Terminal 2: `./scripts/concurrent-booking-demo.sh`

It creates a slot and fires 10 booking requests at the same instant. Expected output: exactly
one `HTTP 201` and nine `HTTP 409`.

### Automated proof

`BookingConcurrencyTest` starts 20 threads (each with its own DB connection/transaction) that
book the same slot simultaneously and asserts: exactly **1** success, **19** conflicts, exactly
1 `ACTIVE` booking in the DB and the slot is `BOOKED`. It also covers re-booking after a
cancellation, concurrent bookings of *different* slots (no unnecessary blocking) and concurrent
cancellation of the same booking.

## 6. Security rules

* All endpoints require authentication (HTTP Basic, stateless).
* Role rules in `SecurityConfig` (ADMIN creates slots / cancels any booking, USER books and
  cancels own booking, both can list slots).
* Ownership rule ("user can cancel only their own booking") is enforced in `BookingService`
  inside the transaction, returning `403` otherwise.

## 7. Tests

| Test class                      | What it covers                                                             |
|---------------------------------|----------------------------------------------------------------------------|
| `BookingConcurrencyTest`        | 20 simultaneous bookings, rebooking races, independent slots, double cancel |
| `BookingServiceTest`            | book / cancel / admin cancel / ownership / not found / rebook              |
| `SlotServiceTest`               | create, validation, ordering                                               |
| `BookingApiIntegrationTest`     | every endpoint via MockMvc: 401 / 403 / 400 / 404 / 409 and happy paths     |
| `GlobalExceptionHandlerTest`    | exception -> HTTP status mapping incl. lock timeout / optimistic failure    |
| `BookingApplicationTests`       | Spring context starts                                                      |

`mvn verify` enforces **>= 80 % line coverage** with JaCoCo
(report: `target/site/jacoco/index.html`).

## 8. Project structure

```
src/main/java/com/techpulse/booking
  controller/   SlotController, BookingController, AdminBookingController
  service/      SlotService, BookingService          (transaction + locking logic)
  repository/   SlotRepository (FOR UPDATE), BookingRepository, UserRepository
  entity/       Slot, Booking, AppUser + enums
  dto/          request / response records
  security/     SecurityConfig, DbUserDetailsService, DataInitializer
  exception/    custom exceptions + GlobalExceptionHandler
```
