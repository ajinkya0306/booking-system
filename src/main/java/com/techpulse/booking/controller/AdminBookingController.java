package com.techpulse.booking.controller;

import com.techpulse.booking.dto.BookingResponse;
import com.techpulse.booking.service.BookingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** ADMIN only - cancels any booking. */
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable("id") Long id, Authentication authentication) {
        return bookingService.cancelAnyBooking(id, authentication.getName());
    }
}
