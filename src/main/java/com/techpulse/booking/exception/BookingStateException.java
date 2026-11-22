package com.techpulse.booking.exception;

/** Thrown when a booking is not in a state that allows the operation. */
public class BookingStateException extends RuntimeException {

    public BookingStateException(String message) {
        super(message);
    }
}
