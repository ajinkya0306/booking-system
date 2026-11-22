package com.techpulse.booking.controller;

import com.techpulse.booking.dto.CreateSlotRequest;
import com.techpulse.booking.dto.SlotResponse;
import com.techpulse.booking.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    /** ADMIN only (enforced in SecurityConfig). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse createSlot(@Valid @RequestBody CreateSlotRequest request) {
        return slotService.createSlot(request);
    }

    /** USER and ADMIN. */
    @GetMapping
    public List<SlotResponse> listSlots() {
        return slotService.listSlots();
    }
}
