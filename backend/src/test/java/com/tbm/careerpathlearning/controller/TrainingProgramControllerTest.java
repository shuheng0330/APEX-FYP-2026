package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff with Trainer access, and HR, schedule and change sessions (FR-12-05); everyone signed in can see the
 * calendar. Who acts, and with which permissions, always comes from the sign-in.
 */
@WebMvcTest(controllers = TrainingProgramController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TrainingProgramControllerTest.MethodSecurity.class)
class TrainingProgramControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    private static final String SESSION = "{\"title\":\"Inverter Series 2026 Launch\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TrainingService trainingService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID user = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Everyone signed in can see the calendar, as themselves")
    void calendarForEveryone() throws Exception {
        TrainingSessionDto dto = new TrainingSessionDto();
        dto.setTitle("Inverter Series 2026 Launch");
        when(trainingService.calendar(any(), any(), anyBoolean(), any())).thenReturn(List.of(dto));

        mockMvc.perform(as(get("/api/trainings").param("from", "2026-10-01").param("to", "2026-10-31"), "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Inverter Series 2026 Launch"));

        verify(trainingService).calendar(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false,
                new TrainingViewer(user, false, false, false));
    }

    @Test
    @DisplayName("A trainer schedules in their own name, with their Trainer access")
    void trainerSchedules() throws Exception {
        when(trainingService.schedule(any(), any())).thenReturn(new TrainingSessionDto());

        mockMvc.perform(as(post("/api/trainings"), "ROLE_USER", "CAN_MANAGE_TRAINING")
                        .contentType(MediaType.APPLICATION_JSON).content(SESSION))
                .andExpect(status().isOk());

        verify(trainingService).schedule(any(), eq(new TrainingViewer(user, true, false, false)));
    }

    @Test
    @DisplayName("HR deletes and cancels as HR")
    void hrManages() throws Exception {
        mockMvc.perform(as(delete("/api/trainings/7"), "ROLE_USER", "CAN_ASSIGN_TRAINING"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/trainings/7/cancel"), "ROLE_USER", "CAN_ASSIGN_TRAINING")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"The trainer is unwell.\"}"))
                .andExpect(status().isOk());

        TrainingViewer hr = new TrainingViewer(user, false, true, false);
        verify(trainingService).delete(7L, hr);
        verify(trainingService).cancel(7L, "The trainer is unwell.", hr);
    }

    @Test
    @DisplayName("Other staff are refused every scheduling action (FR-12-05)")
    void othersAreRefused() throws Exception {
        String[] staff = {"ROLE_USER"};

        mockMvc.perform(as(post("/api/trainings"), staff).contentType(MediaType.APPLICATION_JSON).content(SESSION))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/trainings/7"), staff).contentType(MediaType.APPLICATION_JSON).content(SESSION))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/trainings/7"), staff)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/7/cancel"), staff)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/7/archive"), staff)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/7/restore"), staff)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/trainings/trainers"), staff)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/trainings/audience-check"), staff)
                .contentType(MediaType.APPLICATION_JSON).content(SESSION)).andExpect(status().isForbidden());

        verifyNoInteractions(trainingService);
    }

    /** Signs the request in with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
