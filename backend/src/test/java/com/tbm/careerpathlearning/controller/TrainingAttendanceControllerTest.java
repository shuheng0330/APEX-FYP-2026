package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.CheckInResponseDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingAttendanceDto;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TrainingAttendanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrainingAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrainingAttendanceService trainingAttendanceService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ValidationService validationService;

    private TrainingAttendanceDto attendanceDto;
    private CheckInResponseDto checkInResponse;
    private final UUID STAFF_UUID = UUID.randomUUID();
    private final Long TRAINING_ID = 100L;

    @BeforeEach
    void setUp() {
        attendanceDto = new TrainingAttendanceDto();
        attendanceDto.setStaffId(STAFF_UUID);
        attendanceDto.setTrainingId(TRAINING_ID);
        // Assuming your DTO has latitude/longitude for check-in
        attendanceDto.setCheckInLatitude(3.1390);
        attendanceDto.setCheckInLongitude(101.6869);

        checkInResponse = new CheckInResponseDto();
        checkInResponse.setSuccess(true);
        checkInResponse.setMessage("Check-in successful");
    }

    @Test
    @DisplayName("POST /checkin - Should return success response")
    void checkIn_Success() throws Exception {
        when(trainingAttendanceService.checkIn(any(TrainingAttendanceDto.class)))
                .thenReturn(checkInResponse);

        mockMvc.perform(post("/api/trainings/attendance/checkin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attendanceDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Check-in successful"));
    }

    @Test
    @DisplayName("GET /{trainingId} - Should return attendance list")
    void getAttendanceByTrainingId_Success() throws Exception {
        when(trainingAttendanceService.findTrainingAttendanceByTrainingId(TRAINING_ID))
                .thenReturn(List.of(attendanceDto));

        mockMvc.perform(get("/api/trainings/attendance/" + TRAINING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainingId").value(TRAINING_ID));
    }

    @Test
    @DisplayName("GET /by-staff/{staffId} - Should return list of training IDs")
    void getAttendedTrainingByStaffId_Success() throws Exception {
        when(trainingAttendanceService.getAttendedTrainingByStaffId(STAFF_UUID))
                .thenReturn(List.of(TRAINING_ID));

        mockMvc.perform(get("/api/trainings/attendance/by-staff/" + STAFF_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(TRAINING_ID));
    }

    @Test
    @DisplayName("GET /staff/{trainingId} - Should return staff details list")
    void getStaffAttendanceList_Success() throws Exception {
        StaffDto staff = new StaffDto();
        staff.setId(STAFF_UUID);
        staff.setName("John Doe");

        when(trainingAttendanceService.getStaffAttendanceListByTrainingId(TRAINING_ID))
                .thenReturn(List.of(staff));

        mockMvc.perform(get("/api/trainings/attendance/staff/" + TRAINING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"));
    }
}