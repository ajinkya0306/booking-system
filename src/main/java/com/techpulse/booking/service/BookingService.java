package com.techpulse.booking.service;

import com.techpulse.booking.dto.BookingResponse;
import com.techpulse.booking.entity.AppUser;
import com.techpulse.booking.entity.Booking;
import com.techpulse.booking.entity.BookingStatus;
import com.techpulse.booking.entity.Slot;
import com.techpulse.booking.entity.SlotStatus;
import com.techpulse.booking.exception.BookingStateException;
import com.techpulse.booking.exception.ForbiddenOperationException;
import com.techpulse.booking.exception.ResourceNotFoundException;
import com.techpulse.booking.exception.SlotAlreadyBookedException;
import com.techpulse.booking.repository.BookingRepository;
import com.techpulse.booking.repository.SlotRepository;
import com.techpulse.booking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business logic.
 *
 * Transaction boundary: every public method is ONE database transaction. The slot row is
 * locked (SELECT ... FOR UPDATE) before anything is checked or changed, so the
 * "check slot status, update slot, insert booking" sequence is atomic with respect to every
 * other request. Any exception rolls the whole transaction back -> no partial updates.
 *
 * Lock ordering: both book and cancel always lock the SLOT row first, so two transactions
 * can never wait on each other in a cycle (no deadlocks).
 */
@Service
public class BookingService {

    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public BookingService(SlotRepository slotRepository,
                          BookingRepository bookingRepository,
                          UserRepository userRepository) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BookingResponse bookSlot(Long slotId, String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // 1. Acquire the row lock. Concurrent requests for the same slot queue up here.
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));

        // 2. We now hold the lock and see the latest committed state.
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotAlreadyBookedException(slotId);
        }

        // 3. Update slot + insert booking in the same transaction (atomic).
        slot.setStatus(SlotStatus.BOOKED);
        Booking booking = bookingRepository.save(new Booking(slot, user));

        return BookingResponse.from(booking);
    }

    /** A user cancelling their own booking. */
    @Transactional
    public BookingResponse cancelOwnBooking(Long bookingId, String username) {
        return cancel(bookingId, username, false);
    }

    /** An admin cancelling any booking. */
    @Transactional
    public BookingResponse cancelAnyBooking(Long bookingId, String adminUsername) {
        return cancel(bookingId, adminUsername, true);
    }

    private BookingResponse cancel(Long bookingId, String requester, boolean isAdmin) {
        // Find the slot id WITHOUT loading the Booking entity, so that the booking is read
        // fresh (after the lock is held) and never from a stale persistence-context copy.
        Long slotId = bookingRepository.findSlotIdByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        // Same lock order as bookSlot: slot first.
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!isAdmin && !booking.getUser().getUsername().equals(requester)) {
            throw new ForbiddenOperationException("You can cancel only your own booking");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingStateException("Booking " + bookingId + " is already cancelled");
        }

        booking.cancel();
        slot.setStatus(SlotStatus.AVAILABLE);

        return BookingResponse.from(booking);
    }
}
