package com.techpulse.booking.repository;

import com.techpulse.booking.entity.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findAllByOrderByStartTimeAsc();

    /**
     * Loads the slot with a PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE).
     * Any other transaction trying to lock the same row blocks until this transaction
     * commits or rolls back, then sees the committed state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") Long id);
}
