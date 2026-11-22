package com.techpulse.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Database-level guarantee of "only one ACTIVE booking per slot".
     * Holds the slot id while the booking is ACTIVE and NULL once it is CANCELLED.
     * The UNIQUE constraint means that even if application logic were bypassed, the
     * database itself would reject a second active booking for the same slot
     * (NULLs are not considered equal, so any number of cancelled bookings is fine).
     */
    @Column(name = "active_slot_id", unique = true)
    private Long activeSlotId;

    public Booking() {
    }

    public Booking(Slot slot, AppUser user) {
        this.slot = slot;
        this.user = user;
        this.status = BookingStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.activeSlotId = slot.getId();
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
        this.activeSlotId = null;
    }

    public Long getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public AppUser getUser() {
        return user;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getActiveSlotId() {
        return activeSlotId;
    }
}
