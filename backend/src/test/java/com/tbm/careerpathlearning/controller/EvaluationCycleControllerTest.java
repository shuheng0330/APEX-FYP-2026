package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.EvaluationCycleDto;
import com.tbm.careerpathlearning.service.EvaluationCycleService;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EvaluationCycleController.class)
@AutoConfigureMockMvc(addFilters = false) // Disables filter chain but keeps Method Security (PreAuthorize)
class EvaluationCycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EvaluationCycleService evaluationCycleService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ValidationService validationService;

    private EvaluationCycleDto sampleCycle;
    private Authentication mockAuth;
    private final UUID USER_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Mock the Authentication object to return the UUID string as principal
        mockAuth = mock(Authentication.class);
        when(mockAuth.getName()).thenReturn(USER_UUID.toString());
        when(mockAuth.getPrincipal()).thenReturn(USER_UUID.toString());

        sampleCycle = new EvaluationCycleDto();
        sampleCycle.setId(1L);
        sampleCycle.setStartDate(LocalDate.now());
        sampleCycle.setEndDate(LocalDate.now().plusMonths(6));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_EVALUATION_CYCLE")
    @DisplayName("POST /api/evaluation-cycle - Should return 201 Created")
    void openNewCycle_Authorized_ShouldReturnCreated() throws Exception {
        doNothing().when(evaluationCycleService).openNewCycle(any(), eq(USER_UUID));
        when(evaluationCycleService.getCurrentCycle()).thenReturn(sampleCycle);

        mockMvc.perform(post("/api/evaluation-cycle")
                        .with(csrf()) // Fixes 403 Forbidden
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCycle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_EVALUATION_CYCLE")
    @DisplayName("PUT /api/evaluation-cycle/update/{id} - Should return 200 OK")
    void updateCycle_Authorized_ShouldReturnOk() throws Exception {
        when(evaluationCycleService.updateEvaluationCycle(eq(1L), any(), eq(USER_UUID)))
                .thenReturn(sampleCycle);

        mockMvc.perform(put("/api/evaluation-cycle/update/1")
                        .with(csrf())
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCycle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("GET /api/evaluation-cycle/current - Should return current cycle")
    void getCurrentCycle_ShouldReturnOk() throws Exception {
        when(evaluationCycleService.getCurrentCycle()).thenReturn(sampleCycle);

        mockMvc.perform(get("/api/evaluation-cycle/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }
}