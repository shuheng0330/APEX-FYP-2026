package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.BulkInvitationDTO;
import com.tbm.careerpathlearning.dto.TrainingInvitationDTO;
import com.tbm.careerpathlearning.dto.UpdateInvitationDto;
import com.tbm.careerpathlearning.enums.Status;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingInvitationService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TrainingInvitationController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrainingInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private TrainingInvitationService invitationService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private Authentication mockAuth;
    private final UUID ADMIN_UUID = UUID.randomUUID();
    private TrainingInvitationDTO sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = new TrainingInvitationDTO();
        sampleDto.setInvitationId(1L);
        sampleDto.setStatus(Status.PENDING);

        mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(ADMIN_UUID.toString());
    }

    @Test
    @WithMockUser(authorities = "CAN_ASSIGN_TRAINING")
    @DisplayName("POST /create - Should return 200 OK with list of invitations")
    void inviteStaff_Success() throws Exception {
        BulkInvitationDTO bulkDto = new BulkInvitationDTO();
        bulkDto.setTrainingId(10L);
        bulkDto.setStaffIds(List.of(UUID.randomUUID()));

        when(invitationService.inviteStaff(any(), eq(ADMIN_UUID))).thenReturn(List.of(sampleDto));

        mockMvc.perform(post("/api/training/invitation/create")
                        .with(csrf())
                        .principal(mockAuth) // Bridge to the 'authentication' parameter
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invitationId").value(1));
    }

    @Test
    @DisplayName("GET /get/{trainingId} - Should return invitations for training")
    void getAllByTraining_Success() throws Exception {
        when(invitationService.getAllInvitationsByTraining(10L)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/training/invitation/get/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("PATCH /{id}/status - Should update status")
    void updateInvitationStatus_Success() throws Exception {
        UpdateInvitationDto updateDto = new UpdateInvitationDto();
        updateDto.setStatus(Status.ACCEPTED);

        sampleDto.setStatus(Status.ACCEPTED);
        when(invitationService.updateInvitationStatus(eq(1L), any())).thenReturn(sampleDto);

        mockMvc.perform(patch("/api/training/invitation/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("GET /api/training/invitation - Should return all invitations")
    void getAllInvitations_Success() throws Exception {
        // This covers the currently 0% getAllInvitations() method
        when(invitationService.getTrainingInvitations()).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/training/invitation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/training/invitation/staff/{staffId} - Should return staff invitations")
    void getTrainingInvitationsByStaffId_Success() throws Exception {
        // This covers the currently 0% getTrainingInvitationsByStaffId() method
        UUID staffId = UUID.randomUUID();
        when(invitationService.getTrainingInvitationsByStaffId(staffId)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/training/invitation/staff/" + staffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/training/invitation/get/{trainingId} - Should return training specific invitations")
    void getAllInvitationsByTraining_Success() throws Exception {
        // Reinforces the 100% covered getAllInvitationsByTraining method
        Long trainingId = 10L;
        when(invitationService.getAllInvitationsByTraining(trainingId)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/training/invitation/get/" + trainingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}