package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.StaffLearningDocumentProgressDto;
import com.tbm.careerpathlearning.dto.UpdateDocumentProgressDto;
import com.tbm.careerpathlearning.service.StaffLearningDocumentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StaffLearningDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class StaffLearningDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffLearningDocumentService staffLearningDocumentService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ValidationService validationService;

    private final UUID STAFF_ID = UUID.randomUUID();
    private final Long MATERIAL_ID = 100L;
    private final Long DOCUMENT_ID = 500L;

    private StaffLearningDocumentProgressDto progressDto;

    @BeforeEach
    void setUp() {
        progressDto = new StaffLearningDocumentProgressDto();
        progressDto.setDocumentId(DOCUMENT_ID);
        progressDto.setCompleted(true);
    }

    @Test
    @DisplayName("POST /update - Should return 200 OK")
    void updateDocumentProgress_ShouldReturnOk() throws Exception {
        UpdateDocumentProgressDto updateDto = new UpdateDocumentProgressDto();
        updateDto.setStaffId(STAFF_ID);
        updateDto.setDocumentId(DOCUMENT_ID);

        // Service returns void, so we just mock the behavior
        doNothing().when(staffLearningDocumentService).updateDocumentProgress(any());

        mockMvc.perform(post("/api/learning-document/progress/update")
                        .with(csrf()) // Prevents 403 Forbidden
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        verify(staffLearningDocumentService, times(1)).updateDocumentProgress(any());
    }

    @Test
    @DisplayName("GET /{staffId}/{materialId}/{documentId} - Should return progress")
    void getProgress_ShouldReturnDto() throws Exception {
        when(staffLearningDocumentService.getProgress(STAFF_ID, MATERIAL_ID, DOCUMENT_ID))
                .thenReturn(progressDto);

        mockMvc.perform(get("/api/learning-document/progress/" + STAFF_ID + "/" + MATERIAL_ID + "/" + DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @DisplayName("GET /all/{staffId}/{materialId} - Should return list of progress")
    void getAllProgressByMaterial_ShouldReturnList() throws Exception {
        when(staffLearningDocumentService.getAllProgressByMaterial(STAFF_ID, MATERIAL_ID))
                .thenReturn(List.of(progressDto));

        mockMvc.perform(get("/api/learning-document/progress/all/" + STAFF_ID + "/" + MATERIAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentId").value(DOCUMENT_ID));
    }
}
