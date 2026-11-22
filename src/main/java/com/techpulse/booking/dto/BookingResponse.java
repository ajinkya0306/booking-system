package com.techpulse.booking.dto;

import com.techpulse.booking.entity.Booking;
import com.techpulse.booking.entity.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(Long id, Long slotId, Long userId, String username,
                              BookingStatus status, LocalDateTime createdAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getUser().getId(),
                booking.getUser().getUsername(),
                booking.getStatus(),
                booking.getCreatedAt());
    }
}
