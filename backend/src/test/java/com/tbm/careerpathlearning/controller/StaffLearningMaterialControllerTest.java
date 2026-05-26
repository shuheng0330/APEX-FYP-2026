package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.MaterialProgressDto;
import com.tbm.careerpathlearning.dto.StaffLearningMaterialDto;
import com.tbm.careerpathlearning.service.StaffLearningMaterialService;
import com.tbm.careerpathlearning.service.TokenService;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StaffLearningMaterialController.class)
@AutoConfigureMockMvc(addFilters = false)
class StaffLearningMaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffLearningMaterialService staffLearningMaterialService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ValidationService validationService;

    private final UUID STAFF_ID = UUID.randomUUID();
    private final Long MATERIAL_ID = 1L;
    private StaffLearningMaterialDto enrollmentDto;

    @BeforeEach
    void setUp() {
        enrollmentDto = new StaffLearningMaterialDto();
        enrollmentDto.setStaffId(STAFF_ID);
        enrollmentDto.setMaterialId(MATERIAL_ID);
    }

    @Test
    @DisplayName("POST /api/learning-material/enroll - Should enroll and return 200 OK")
    void enroll_ShouldReturnOk() throws Exception {
        when(staffLearningMaterialService.enroll(any(StaffLearningMaterialDto.class)))
                .thenReturn(enrollmentDto);

        mockMvc.perform(post("/api/learning-material/enroll")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollmentDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialId").value(MATERIAL_ID));
    }

    @Test
    @DisplayName("GET /my-course/{staffId} - Should return list of courses")
    void getEnrolledCourses_ShouldReturnList() throws Exception {
        LearningMaterialDto course = new LearningMaterialDto();
        course.setMaterialId(MATERIAL_ID);
        course.setTitle("Java Unit Testing");

        when(staffLearningMaterialService.getEnrolledCourses(STAFF_ID))
                .thenReturn(List.of(course));

        mockMvc.perform(get("/api/learning-material/enroll/my-course/" + STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Java Unit Testing"));
    }

    @Test
    @DisplayName("GET /all/progress/{staffId} - Should return progress list")
    void getAllProgress_ShouldReturnList() throws Exception {
        MaterialProgressDto progress = new MaterialProgressDto();
        progress.setProgress(75.0);

        when(staffLearningMaterialService.getProgressList(STAFF_ID))
                .thenReturn(List.of(progress));

        mockMvc.perform(get("/api/learning-material/enroll/all/progress/" + STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].progress").value(75.0));
    }

    @Test
    @DisplayName("DELETE /unenroll/{staffId}/{materialId} - Should return 204 No Content")
    void unenroll_ShouldReturnNoContent() throws Exception {
        doNothing().when(staffLearningMaterialService).unenroll(STAFF_ID, MATERIAL_ID);

        mockMvc.perform(delete("/api/learning-material/enroll/unenroll/" + STAFF_ID + "/" + MATERIAL_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(staffLearningMaterialService, times(1)).unenroll(STAFF_ID, MATERIAL_ID);
    }

    @Test
    @DisplayName("GET /list/{materialId} - Should return enrollments for specific material")
    void getEnrollmentsByMaterialId_Success() throws Exception {
        // Targeted at getEnrollmentsByMaterialId(Long) currently at 0%
        when(staffLearningMaterialService.getEnrollmentsByMaterialId(1L))
                .thenReturn(List.of(enrollmentDto));

        mockMvc.perform(get("/api/learning-material/enroll/list/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].materialId").value(1));
    }

    @Test
    @DisplayName("GET /{staffId}/{materialId} - Should return specific staff enrollment")
    void getLearningMaterialByStaffId_Success() throws Exception {
        // Targeted at getLearningMaterialByStaffId(UUID, Long) currently at 0%
        when(staffLearningMaterialService.getLearningMaterialByStaffId(STAFF_ID, 1L))
                .thenReturn(enrollmentDto);

        mockMvc.perform(get("/api/learning-material/enroll/" + STAFF_ID + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(STAFF_ID.toString()))
                .andExpect(jsonPath("$.materialId").value(1));
    }

}