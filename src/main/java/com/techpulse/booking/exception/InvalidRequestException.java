package com.techpulse.booking.exception;

/** Thrown when request data is semantically invalid. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
