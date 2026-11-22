package com.techpulse.booking.repository;

import com.techpulse.booking.entity.Booking;
import com.techpulse.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    
    @Query("select b.slot.id from Booking b where b.id = :bookingId")
    Optional<Long> findSlotIdByBookingId(@Param("bookingId") Long bookingId);

    long countBySlot_IdAndStatus(Long slotId, BookingStatus status);
}
