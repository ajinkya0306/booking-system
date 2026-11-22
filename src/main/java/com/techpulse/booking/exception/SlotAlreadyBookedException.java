package com.techpulse.booking.exception;

/** Thrown when a slot is not available for booking. */
public class SlotAlreadyBookedException extends RuntimeException {

    public SlotAlreadyBookedException(Long slotId) {
        super("Slot " + slotId + " is already booked");
    }
}
