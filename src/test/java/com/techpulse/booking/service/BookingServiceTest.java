package com.techpulse.booking.service;

import com.techpulse.booking.dto.BookingResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BookingServiceTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private BookingRepository bookingRepository;

    private Long slotId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        slotId = slotRepository.save(new Slot(start, start.plusHours(1))).getId();
    }

    private SlotStatus slotStatus() {
        return slotRepository.findById(slotId).orElseThrow().getStatus();
    }

    @Test
    void bookSlot_success_marksSlotBookedAndCreatesActiveBooking() {
        BookingResponse response = bookingService.bookSlot(slotId, "user1");

        assertNotNull(response.id());
        assertEquals(slotId, response.slotId());
        assertEquals("user1", response.username());
        assertEquals(BookingStatus.ACTIVE, response.status());
        assertNotNull(response.createdAt());
        assertEquals(SlotStatus.BOOKED, slotStatus());
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void bookSlot_alreadyBooked_throwsAndCreatesNoSecondBooking() {
        bookingService.bookSlot(slotId, "user1");

        assertThrows(SlotAlreadyBookedException.class, () -> bookingService.bookSlot(slotId, "user2"));

        assertEquals(1, bookingRepository.count());
        assertEquals(SlotStatus.BOOKED, slotStatus());
    }

    @Test
    void bookSlot_unknownSlot_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> bookingService.bookSlot(987654L, "user1"));
        assertEquals(0, bookingRepository.count());
    }

    @Test
    void bookSlot_unknownUser_throwsNotFoundAndLeavesSlotAvailable() {
        assertThrows(ResourceNotFoundException.class, () -> bookingService.bookSlot(slotId, "ghost"));
        assertEquals(SlotStatus.AVAILABLE, slotStatus());
        assertEquals(0, bookingRepository.count());
    }

    @Test
    void cancelOwnBooking_success_releasesSlot() {
        BookingResponse booking = bookingService.bookSlot(slotId, "user1");

        BookingResponse cancelled = bookingService.cancelOwnBooking(booking.id(), "user1");

        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(SlotStatus.AVAILABLE, slotStatus());
        Booking stored = bookingRepository.findById(booking.id()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, stored.getStatus());
        assertNull(stored.getActiveSlotId());
    }

    @Test
    void cancelOwnBooking_ofAnotherUser_isForbiddenAndChangesNothing() {
        BookingResponse booking = bookingService.bookSlot(slotId, "user1");

        assertThrows(ForbiddenOperationException.class,
                () -> bookingService.cancelOwnBooking(booking.id(), "user2"));

        assertEquals(BookingStatus.ACTIVE, bookingRepository.findById(booking.id()).orElseThrow().getStatus());
        assertEquals(SlotStatus.BOOKED, slotStatus());
    }

    @Test
    void cancelOwnBooking_twice_throwsStateException() {
        BookingResponse booking = bookingService.bookSlot(slotId, "user1");
        bookingService.cancelOwnBooking(booking.id(), "user1");

        assertThrows(BookingStateException.class, () -> bookingService.cancelOwnBooking(booking.id(), "user1"));
    }

    @Test
    void cancelBooking_unknownBooking_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> bookingService.cancelOwnBooking(424242L, "user1"));
        assertThrows(ResourceNotFoundException.class, () -> bookingService.cancelAnyBooking(424242L, "admin"));
    }

    @Test
    void cancelAnyBooking_adminCanCancelOthersBooking() {
        BookingResponse booking = bookingService.bookSlot(slotId, "user1");

        BookingResponse cancelled = bookingService.cancelAnyBooking(booking.id(), "admin");

        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(SlotStatus.AVAILABLE, slotStatus());
    }

    @Test
    void slotCanBeRebookedAfterCancellation() {
        BookingResponse first = bookingService.bookSlot(slotId, "user1");
        bookingService.cancelOwnBooking(first.id(), "user1");

        BookingResponse second = bookingService.bookSlot(slotId, "user2");

        assertEquals(BookingStatus.ACTIVE, second.status());
        assertEquals(SlotStatus.BOOKED, slotStatus());
        assertEquals(2, bookingRepository.count());
        assertEquals(1, bookingRepository.countBySlot_IdAndStatus(slotId, BookingStatus.ACTIVE));
        assertEquals(1, bookingRepository.countBySlot_IdAndStatus(slotId, BookingStatus.CANCELLED));
    }
}
