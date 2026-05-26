package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.EvaluationDTO;
import com.tbm.careerpathlearning.service.EvaluationService;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EvaluationController.class)
@AutoConfigureMockMvc(addFilters = false) // Disables standard filter chain
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EvaluationService evaluationService;

    @MockitoBean
    private TokenService tokenService; // Required for context loading

    @MockitoBean
    private ValidationService validationService;

    private EvaluationDTO sampleDto;
    private final UUID STAFF_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sampleDto = new EvaluationDTO();
        sampleDto.setEvaluationId(1L);
        sampleDto.setStaffId(STAFF_ID);
        sampleDto.setComment("Excellent Performance");
        sampleDto.setOverallScore(90.0);
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_EVALUATION") // Simulates the required authority
    @DisplayName("POST /api/evaluation - Should return 201 Created when authorized")
    void createEvaluation_Authorized_ShouldReturnCreated() throws Exception {
        when(evaluationService.createEvaluation(any(EvaluationDTO.class))).thenReturn(sampleDto);

        mockMvc.perform(post("/api/evaluation")
                        .with(csrf()) // Mandatory for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationId").value(1L))
                .andExpect(jsonPath("$.comment").value("Excellent Performance"));
    }

    @Test
    @DisplayName("GET /api/evaluation - Should return 200 OK and list")
    void getEvaluations_ShouldReturnList() throws Exception {
        when(evaluationService.getAllEvaluations()).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].evaluationId").value(1L));
    }

    @Test
    @DisplayName("GET /api/evaluation/by-staff/{id} - Should return list for staff")
    void getEvaluationsByStaffId_ShouldReturnList() throws Exception {
        when(evaluationService.getAllEvaluationsByStaffId(STAFF_ID)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/evaluation/by-staff/" + STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].staffId").value(STAFF_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/evaluation/direct-down-line/{id} - Should return list for manager")
    void getEvaluationsByDirectDownLineId_ShouldReturnList() throws Exception {
        when(evaluationService.getAllDownLineEvaluation(STAFF_ID)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/evaluation/direct-down-line/" + STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}