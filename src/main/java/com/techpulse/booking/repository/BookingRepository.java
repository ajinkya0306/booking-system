package com.techpulse.booking.repository;

import com.techpulse.booking.entity.Booking;
import com.techpulse.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Returns only the slot id of a booking (scalar projection, the Booking entity is NOT
     * loaded into the persistence context). This lets the cancel flow lock the slot first
     * and only then load a fresh copy of the booking.
     */
    @Query("select b.slot.id from Booking b where b.id = :bookingId")
    Optional<Long> findSlotIdByBookingId(@Param("bookingId") Long bookingId);

    long countBySlot_IdAndStatus(Long slotId, BookingStatus status);
}
