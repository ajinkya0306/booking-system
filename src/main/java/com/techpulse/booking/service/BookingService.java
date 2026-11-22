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

        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotAlreadyBookedException(slotId);
        }

        slot.setStatus(SlotStatus.BOOKED);
        Booking booking = bookingRepository.save(new Booking(slot, user));

        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancelOwnBooking(Long bookingId, String username) {
        return cancel(bookingId, username, false);
    }

    @Transactional
    public BookingResponse cancelAnyBooking(Long bookingId, String adminUsername) {
        return cancel(bookingId, adminUsername, true);
    }

    private BookingResponse cancel(Long bookingId, String requester, boolean isAdmin) {

        Long slotId = bookingRepository.findSlotIdByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

       
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
