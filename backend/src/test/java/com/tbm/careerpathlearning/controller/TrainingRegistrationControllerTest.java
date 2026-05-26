package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import com.tbm.careerpathlearning.dto.TrainingRegistrationDTO;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TrainingRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrainingRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrainingRegistrationService registrationService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ValidationService validationService;

    private TrainingRegistrationDTO sampleRegDto;
    private StaffDto sampleStaffDto;
    private final UUID STAFF_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Mock the nested TrainingProgramDTO
        TrainingProgramDTO trainingDto = new TrainingProgramDTO();
        trainingDto.setTrainingId(100L);
        trainingDto.setTitle("Java Mastery");

        // Set up the main Registration DTO
        sampleRegDto = new TrainingRegistrationDTO();
        sampleRegDto.setRegistrationId(1L);
        sampleRegDto.setTraining(trainingDto); // Matches your private TrainingProgramDTO training;
        sampleRegDto.setStaffId(STAFF_UUID);
        sampleRegDto.setRegisteredAt(LocalDateTime.now());

        sampleStaffDto = new StaffDto();
        sampleStaffDto.setId(STAFF_UUID);
        sampleStaffDto.setName("Test Employee");
        sampleStaffDto.setEmail("test@tbm.com");
    }

    @Test
    @DisplayName("POST / - Should successfully create registration")
    void createRegistration_Success() throws Exception {
        when(registrationService.createTrainingRegistration(any(TrainingRegistrationDTO.class)))
                .thenReturn(sampleRegDto);

        mockMvc.perform(post("/api/register-training")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRegDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationId").value(1))
                .andExpect(jsonPath("$.staffId").value(STAFF_UUID.toString()));
    }

    @Test
    @DisplayName("GET /staff/{trainingId} - Should return list of registered staff")
    void getAllRegisteredStaff_Success() throws Exception {
        when(registrationService.getRegisteredStaffByTrainingId(100L))
                .thenReturn(List.of(sampleStaffDto));

        mockMvc.perform(get("/api/register-training/staff/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Employee"))
                .andExpect(jsonPath("$[0].email").value("test@tbm.com"));
    }
}