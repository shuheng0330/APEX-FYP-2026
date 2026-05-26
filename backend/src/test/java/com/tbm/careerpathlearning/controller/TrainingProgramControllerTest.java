package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TrainingProgramController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrainingProgramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private TrainingService trainingService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private Authentication mockAuth;
    private final UUID USER_UUID = UUID.randomUUID();
    private TrainingProgramDTO sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = new TrainingProgramDTO();
        sampleDto.setTrainingId(1L);
        sampleDto.setTitle("Java Mastery Workshop");

        mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(USER_UUID.toString());
    }

    @Test
    @DisplayName("GET /paged - Should return PageResponse with metadata")
    void getTrainingProgramsPaged_Success() throws Exception {
        // Mock a Spring Data Page object
        Page<TrainingProgramDTO> page = new PageImpl<>(List.of(sampleDto), PageRequest.of(0, 10), 1);
        when(trainingService.getTrainingPrograms(any())).thenReturn(page);

        mockMvc.perform(get("/api/trainings/paged")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Java Mastery Workshop"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_TRAINING")
    @DisplayName("POST / - Should successfully create training")
    void createTraining_Success() throws Exception {
        when(trainingService.createTrainingProgram(any(), eq(USER_UUID))).thenReturn(sampleDto);

        mockMvc.perform(post("/api/trainings")
                        .with(csrf())
                        .principal(mockAuth) // Bridge to the 'authentication' parameter
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingId").value(1));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_TRAINING")
    @DisplayName("PUT /{id} - Should successfully update training")
    void updateTraining_Success() throws Exception {
        when(trainingService.updateTrainingProgram(eq(1L), any(), eq(USER_UUID))).thenReturn(sampleDto);

        mockMvc.perform(put("/api/trainings/1")
                        .with(csrf())
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingId").value(1));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_TRAINING")
    @DisplayName("DELETE /{id} - Should return 200 OK")
    void deleteTraining_Success() throws Exception {
        mockMvc.perform(delete("/api/trainings/1")
                        .with(csrf())
                        .principal(mockAuth))
                .andExpect(status().isOk());

        verify(trainingService).deleteTrainingProgramById(eq(1L), eq(USER_UUID));
    }

    @Test
    @DisplayName("GET /{id} - Should return 404 when training not found (Branch Coverage)")
    void getTrainingProgramById_NotFound() throws Exception {
        when(trainingService.getTrainingProgramById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/trainings/99"))
                .andExpect(status().isNotFound()); // Hits the : ResponseEntity.notFound().build() branch
    }

    @Test
    @DisplayName("GET /registered/{staffId} - Should return list")
    void getTrainingProgramsByStaffId_Success() throws Exception {
        when(trainingService.getTrainingProgramsByStaffId(USER_UUID)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/trainings/registered/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET / - Should return all programs")
    void getAllTrainingPrograms_Success() throws Exception {
        when(trainingService.getAllTrainingPrograms()).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/trainings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /role-options - Should return list")
    void getRoleCascaderOptions_Success() throws Exception {
        when(trainingService.getRoleCascaderOptions()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/trainings/role-options"))
                .andExpect(status().isOk());
    }

}