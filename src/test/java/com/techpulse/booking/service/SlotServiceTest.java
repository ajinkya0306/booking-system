package com.techpulse.booking.service;

import com.techpulse.booking.dto.CreateSlotRequest;
import com.techpulse.booking.dto.SlotResponse;
import com.techpulse.booking.entity.SlotStatus;
import com.techpulse.booking.exception.InvalidRequestException;
import com.techpulse.booking.repository.BookingRepository;
import com.techpulse.booking.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SlotServiceTest {

    @Autowired
    private SlotService slotService;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void cleanUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
    }

    @Test
    void createSlot_persistsAvailableSlot() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        SlotResponse response = slotService.createSlot(new CreateSlotRequest(start, start.plusHours(1)));

        assertNotNull(response.id());
        assertEquals(SlotStatus.AVAILABLE, response.status());
        assertEquals(start, response.startTime());
        assertEquals(1, slotRepository.count());
    }

    @Test
    void createSlot_rejectsEndBeforeStart() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        assertThrows(InvalidRequestException.class,
                () -> slotService.createSlot(new CreateSlotRequest(start, start.minusHours(1))));
        assertThrows(InvalidRequestException.class,
                () -> slotService.createSlot(new CreateSlotRequest(start, start)));
        assertEquals(0, slotRepository.count());
    }

    @Test
    void listSlots_returnsSlotsOrderedByStartTime() {
        LocalDateTime base = LocalDateTime.of(2030, 1, 1, 10, 0);
        slotService.createSlot(new CreateSlotRequest(base.plusHours(5), base.plusHours(6)));
        slotService.createSlot(new CreateSlotRequest(base, base.plusHours(1)));

        List<SlotResponse> slots = slotService.listSlots();

        assertEquals(2, slots.size());
        assertTrue(slots.get(0).startTime().isBefore(slots.get(1).startTime()));
    }
}
