package com.techpulse.booking.exception;

/** Thrown when a user tries to act on a resource they do not own. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
