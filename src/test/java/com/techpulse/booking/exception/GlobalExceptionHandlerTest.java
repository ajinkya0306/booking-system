package com.techpulse.booking.exception;

import com.techpulse.booking.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertStatus(ResponseEntity<ErrorResponse> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expected.value(), response.getBody().status());
        assertNotNull(response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void notFound_is404() {
        assertStatus(handler.handleNotFound(new ResourceNotFoundException("missing")), HttpStatus.NOT_FOUND);
    }

    @Test
    void slotAlreadyBooked_is409WithClearMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleSlotAlreadyBooked(new SlotAlreadyBookedException(7L));
        assertStatus(response, HttpStatus.CONFLICT);
        assertEquals("Slot 7 is already booked", response.getBody().message());
    }

    @Test
    void bookingState_is409() {
        assertStatus(handler.handleBookingState(new BookingStateException("already cancelled")), HttpStatus.CONFLICT);
    }

    @Test
    void forbidden_is403() {
        assertStatus(handler.handleForbidden(new ForbiddenOperationException("nope")), HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidRequest_is400() {
        assertStatus(handler.handleInvalidRequest(new InvalidRequestException("bad")), HttpStatus.BAD_REQUEST);
    }

    @Test
    void optimisticLockFailure_is409() {
        assertStatus(handler.handleConcurrencyFailure(
                new ObjectOptimisticLockingFailureException(Object.class, 1L)), HttpStatus.CONFLICT);
    }

    @Test
    void pessimisticLockTimeout_is409() {
        assertStatus(handler.handleConcurrencyFailure(new CannotAcquireLockException("timeout")), HttpStatus.CONFLICT);
    }

    @Test
    void uniqueConstraintViolation_is409() {
        assertStatus(handler.handleDataIntegrity(new DataIntegrityViolationException("unique")), HttpStatus.CONFLICT);
    }

    @Test
    void unexpectedError_is500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("secret detail"));
        assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
        assertEquals("Unexpected server error", response.getBody().message());
    }
}
