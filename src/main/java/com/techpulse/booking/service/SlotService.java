package com.techpulse.booking.service;

import com.techpulse.booking.dto.CreateSlotRequest;
import com.techpulse.booking.dto.SlotResponse;
import com.techpulse.booking.entity.Slot;
import com.techpulse.booking.exception.InvalidRequestException;
import com.techpulse.booking.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlotService {

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidRequestException("endTime must be after startTime");
        }
        Slot slot = slotRepository.save(new Slot(request.startTime(), request.endTime()));
        return SlotResponse.from(slot);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listSlots() {
        return slotRepository.findAllByOrderByStartTimeAsc().stream()
                .map(SlotResponse::from)
                .toList();
    }
}
