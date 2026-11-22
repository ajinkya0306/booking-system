package com.techpulse.booking.exception;

/** Thrown when a slot, booking or user does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
