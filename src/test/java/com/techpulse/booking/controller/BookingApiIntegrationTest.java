package com.techpulse.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.booking.repository.BookingRepository;
import com.techpulse.booking.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests of the REST API including authentication and authorization rules. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiIntegrationTest {

    private static final String SLOT_JSON =
            "{\"startTime\":\"2030-01-01T10:00:00\",\"endTime\":\"2030-01-01T11:00:00\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private BookingRepository bookingRepository;

    private static RequestPostProcessor admin() {
        return httpBasic("admin", "admin123");
    }

    private static RequestPostProcessor user1() {
        return httpBasic("user1", "user123");
    }

    private static RequestPostProcessor user2() {
        return httpBasic("user2", "user123");
    }

    @BeforeEach
    void cleanUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
    }

    private long createSlot() throws Exception {
        MvcResult result = mvc.perform(post("/slots").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(SLOT_JSON))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long book(long slotId, RequestPostProcessor who) throws Exception {
        MvcResult result = mvc.perform(post("/bookings").with(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---------------- authentication / authorization ----------------

    @Test
    void unauthenticatedRequests_areRejectedWith401() throws Exception {
        mvc.perform(get("/slots")).andExpect(status().isUnauthorized());
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/slots").contentType(MediaType.APPLICATION_JSON).content(SLOT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPassword_isRejectedWith401() throws Exception {
        mvc.perform(get("/slots").with(httpBasic("user1", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void user_cannotCreateSlot() throws Exception {
        mvc.perform(post("/slots").with(user1()).contentType(MediaType.APPLICATION_JSON).content(SLOT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_cannotBookSlot() throws Exception {
        long slotId = createSlot();
        mvc.perform(post("/bookings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_cannotUseAdminCancelEndpoint() throws Exception {
        long slotId = createSlot();
        long bookingId = book(slotId, user1());
        mvc.perform(post("/admin/bookings/" + bookingId + "/cancel").with(user1()))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_cannotUseUserCancelEndpoint() throws Exception {
        long slotId = createSlot();
        long bookingId = book(slotId, user1());
        mvc.perform(post("/bookings/" + bookingId + "/cancel").with(admin()))
                .andExpect(status().isForbidden());
    }

    // ---------------- slots ----------------

    @Test
    void admin_createsSlot_andBothRolesCanListSlots() throws Exception {
        long slotId = createSlot();

        mvc.perform(get("/slots").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(slotId))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
        mvc.perform(get("/slots").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createSlot_withEndBeforeStart_returns400() throws Exception {
        mvc.perform(post("/slots").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2030-01-01T11:00:00\",\"endTime\":\"2030-01-01T10:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endTime must be after startTime"));
    }

    @Test
    void createSlot_withMissingFields_returns400() throws Exception {
        mvc.perform(post("/slots").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSlot_withMalformedJson_returns400() throws Exception {
        mvc.perform(post("/slots").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- booking ----------------

    @Test
    void booking_marksSlotBooked() throws Exception {
        long slotId = createSlot();
        book(slotId, user1());

        mvc.perform(get("/slots").with(user1()))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
    }

    @Test
    void secondBookingOfSameSlot_returns409WithClearMessage() throws Exception {
        long slotId = createSlot();
        book(slotId, user1());

        mvc.perform(post("/bookings").with(user2()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Slot " + slotId + " is already booked"));
    }

    @Test
    void booking_unknownSlot_returns404() throws Exception {
        mvc.perform(post("/bookings").with(user1()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void booking_withoutSlotId_returns400() throws Exception {
        mvc.perform(post("/bookings").with(user1()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- cancellation ----------------

    @Test
    void user_cancelsOwnBooking_slotBecomesAvailableAgain() throws Exception {
        long slotId = createSlot();
        long bookingId = book(slotId, user1());

        mvc.perform(post("/bookings/" + bookingId + "/cancel").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/slots").with(user1()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        // and somebody else can now book it
        book(slotId, user2());
    }

    @Test
    void user_cannotCancelSomeoneElsesBooking() throws Exception {
        long slotId = createSlot();
        long bookingId = book(slotId, user1());

        mvc.perform(post("/bookings/" + bookingId + "/cancel").with(user2()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/slots").with(user1()))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));
    }

    @Test
    void admin_cancelsAnyBooking() throws Exception {
        long slotId = createSlot();
        long bookingId = book(slotId, user1());

        mvc.perform(post("/admin/bookings/" + bookingId + "/cancel").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/slots").with(admin()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void cancellingTwice_returns409() throws Exception {
        long slotId = createSlot();
        long bookingId = book(slotId, user1());
        mvc.perform(post("/bookings/" + bookingId + "/cancel").with(user1())).andExpect(status().isOk());

        mvc.perform(post("/bookings/" + bookingId + "/cancel").with(user1()))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelUnknownBooking_returns404() throws Exception {
        mvc.perform(post("/bookings/999999/cancel").with(user1())).andExpect(status().isNotFound());
        mvc.perform(post("/admin/bookings/999999/cancel").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void cancelWithNonNumericId_returns400() throws Exception {
        mvc.perform(post("/bookings/abc/cancel").with(user1())).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        mvc.perform(get("/bookings").with(user1())).andExpect(status().isMethodNotAllowed());
    }
}
