package com.techpulse.booking.service;

import com.techpulse.booking.entity.AppUser;
import com.techpulse.booking.entity.BookingStatus;
import com.techpulse.booking.entity.Role;
import com.techpulse.booking.entity.Slot;
import com.techpulse.booking.entity.SlotStatus;
import com.techpulse.booking.exception.SlotAlreadyBookedException;
import com.techpulse.booking.repository.BookingRepository;
import com.techpulse.booking.repository.SlotRepository;
import com.techpulse.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the race-condition protection: many threads (each with its own DB transaction and
 * connection) try to book at the same moment and exactly one may win.
 */
@SpringBootTest
class BookingConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> racers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        racers.clear();
        String encoded = passwordEncoder.encode("racer-pass");
        for (int i = 0; i < THREADS; i++) {
            String username = "racer" + i;
            racers.add(username);
            if (userRepository.findByUsername(username).isEmpty()) {
                userRepository.save(new AppUser(username, encoded, Role.USER));
            }
        }
    }

    private Slot newSlot(int offsetHours) {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 8, 0).plusHours(offsetHours);
        return slotRepository.save(new Slot(start, start.plusHours(1)));
    }

    private static boolean isExpectedConflict(Throwable t) {
        return t instanceof SlotAlreadyBookedException
                || t instanceof ConcurrencyFailureException
                || t instanceof DataIntegrityViolationException;
    }

    /** Runs all tasks at the same instant and waits for them. */
    private Queue<Throwable> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        task.call();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    @Test
    void manyConcurrentRequestsForSameSlot_exactlyOneSucceeds() throws Exception {
        Slot slot = newSlot(0);
        AtomicInteger successes = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (String user : racers) {
            tasks.add(() -> {
                bookingService.bookSlot(slot.getId(), user);
                successes.incrementAndGet();
                return null;
            });
        }
        Queue<Throwable> failures = runConcurrently(tasks);

        assertEquals(1, successes.get(), "exactly one booking must succeed");
        assertEquals(THREADS - 1, failures.size());
        failures.forEach(t -> assertTrue(isExpectedConflict(t), "unexpected failure type: " + t));

        assertEquals(1, bookingRepository.countBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE));
        assertEquals(1, bookingRepository.count());
        assertEquals(SlotStatus.BOOKED, slotRepository.findById(slot.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentRequestsAfterCancellation_exactlyOneRebookSucceeds() throws Exception {
        Slot slot = newSlot(0);
        Long firstBooking = bookingService.bookSlot(slot.getId(), "racer0").id();
        bookingService.cancelOwnBooking(firstBooking, "racer0");

        AtomicInteger successes = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (String user : racers) {
            tasks.add(() -> {
                bookingService.bookSlot(slot.getId(), user);
                successes.incrementAndGet();
                return null;
            });
        }
        Queue<Throwable> failures = runConcurrently(tasks);

        assertEquals(1, successes.get());
        failures.forEach(t -> assertTrue(isExpectedConflict(t), "unexpected failure type: " + t));
        assertEquals(1, bookingRepository.countBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE));
        assertEquals(1, bookingRepository.countBySlot_IdAndStatus(slot.getId(), BookingStatus.CANCELLED));
        assertEquals(SlotStatus.BOOKED, slotRepository.findById(slot.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentRequestsForDifferentSlots_allSucceed() throws Exception {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            slots.add(newSlot(i));
        }
        AtomicInteger successes = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Long slotId = slots.get(i).getId();
            String user = racers.get(i);
            tasks.add(() -> {
                bookingService.bookSlot(slotId, user);
                successes.incrementAndGet();
                return null;
            });
        }
        Queue<Throwable> failures = runConcurrently(tasks);

        assertTrue(failures.isEmpty(), "independent slots must never conflict: " + failures);
        assertEquals(5, successes.get());
        assertEquals(5, bookingRepository.count());
        slots.forEach(s -> assertEquals(SlotStatus.BOOKED,
                slotRepository.findById(s.getId()).orElseThrow().getStatus()));
    }

    @Test
    void concurrentCancelOfSameBooking_exactlyOneSucceeds() throws Exception {
        Slot slot = newSlot(0);
        Long bookingId = bookingService.bookSlot(slot.getId(), "racer0").id();
        AtomicInteger successes = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                bookingService.cancelOwnBooking(bookingId, "racer0");
                successes.incrementAndGet();
                return null;
            });
        }
        Queue<Throwable> failures = runConcurrently(tasks);

        assertEquals(1, successes.get());
        assertEquals(4, failures.size());
        assertEquals(SlotStatus.AVAILABLE, slotRepository.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals(0, bookingRepository.countBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE));
    }
}
