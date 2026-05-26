package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.LearningDocumentDto;
import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.LearningMaterialRequestDto;
import com.tbm.careerpathlearning.model.LearningDocument;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LearningMaterialController.class)
@AutoConfigureMockMvc(addFilters = false)
class LearningMaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private LearningMaterialService learningMaterialService;
    @MockitoBean private LearningMaterialRecommendationService recommendationService;
    @MockitoBean private FileService fileService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private Authentication mockAuth;
    private final UUID USER_UUID = UUID.randomUUID();
    private LearningMaterialDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = new LearningMaterialDto();
        sampleDto.setMaterialId(1L);
        sampleDto.setTitle("Spring Security 101");

        mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(USER_UUID.toString());
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("POST /create - Should return 201 Created")
    void createLearningMaterial_Success() throws Exception {
        LearningMaterialRequestDto requestDto = new LearningMaterialRequestDto();
        requestDto.setTitle("Spring Security 101");
        requestDto.setDescription("Detailed course on Spring Security"); // Required
        requestDto.setMaterialType(List.of("PDF")); // Required
        requestDto.setDepartmentIds(List.of(1L)); // Required
        requestDto.setCompetencyIds(List.of(10L)); // Required

        LearningDocumentDto docDto = new LearningDocumentDto();
        docDto.setTitle("Introduction");
        docDto.setFileUrl("http://s3.com/test.pdf");
        requestDto.setLearningDocuments(List.of(docDto));

        when(learningMaterialService.createLearningMaterial(any(), eq(USER_UUID))).thenReturn(sampleDto);

        mockMvc.perform(post("/api/learning-material/create")
                        .with(csrf())
                        .principal(mockAuth) // Required for the UUID parsing
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spring Security 101"));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("POST /upload - Should handle multipart file")
    void uploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf",
                MediaType.APPLICATION_PDF_VALUE, "PDF content".getBytes());

        when(fileService.uploadFile(any(), eq("LMS Material"))).thenReturn("http://s3.com/test.pdf");

        mockMvc.perform(multipart("/api/learning-material/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value("http://s3.com/test.pdf"));
    }



    @Test
    @DisplayName("GET /recommendation/{staffId} - Should return list")
    void getRecommendation_Success() throws Exception {
        when(recommendationService.recommendForUser(USER_UUID)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/learning-material/recommendation/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("DELETE /bulk-delete - Should handle query params")
    void bulkDelete_Success() throws Exception {
        mockMvc.perform(delete("/api/learning-material/bulk-delete")
                        .param("materialIds", "1,2,3")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(learningMaterialService).bulkDeleteLearningMaterial(List.of(1L, 2L, 3L));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("POST /upload-document - Should successfully upload multiple documents")
    void uploadDocuments_Success() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "doc1.pdf", MediaType.APPLICATION_PDF_VALUE, "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "doc2.pdf", MediaType.APPLICATION_PDF_VALUE, "content2".getBytes());

        // Titles parts must be sent as MockMultipartFile if using @RequestPart
        MockMultipartFile titlesPart = new MockMultipartFile("titles", "", "application/json", objectMapper.writeValueAsBytes(List.of("Title 1", "Title 2")));

        LearningDocument doc = new LearningDocument();
        doc.setTitle("Title 1");

        when(learningMaterialService.uploadDocuments(anyList(), anyList(), eq("LMS Material"))).thenReturn(List.of(doc));

        mockMvc.perform(multipart("/api/learning-material/upload-document")
                        .file(file1)
                        .file(file2)
                        .file(titlesPart)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Title 1"));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("POST /upload-document - Should return 400 when sizes mismatch")
    void uploadDocuments_SizeMismatch_BadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", MediaType.TEXT_PLAIN_VALUE, "test".getBytes());
        MockMultipartFile titlesPart = new MockMultipartFile("titles", "", "application/json", objectMapper.writeValueAsBytes(List.of("T1", "T2")));

        mockMvc.perform(multipart("/api/learning-material/upload-document")
                        .file(file)
                        .file(titlesPart)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{id} - Should return 404 when material not found")
    void getLearningMaterialById_NotFound() throws Exception {
        when(learningMaterialService.getLearningMaterialById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/learning-material/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("PUT /update/{id} - Should return 200 on successful update")
    void updateLearningMaterial_Success() throws Exception {
        LearningMaterialRequestDto requestDto = new LearningMaterialRequestDto();
        requestDto.setTitle("Updated Title");
        requestDto.setDescription("Detailed course on Spring Security"); // Required
        requestDto.setMaterialType(List.of("PDF")); // Required
        requestDto.setDepartmentIds(List.of(1L)); // Required
        requestDto.setCompetencyIds(List.of(10L)); // Required

        LearningDocumentDto docDto = new LearningDocumentDto();
        docDto.setTitle("Introduction");
        docDto.setFileUrl("http://s3.com/test.pdf");
        requestDto.setLearningDocuments(List.of(docDto));

        when(learningMaterialService.updateLearningMaterial(eq(1L), any())).thenReturn(sampleDto);

        mockMvc.perform(put("/api/learning-material/update/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("DELETE /delete/{id} - Should return 200")
    void deleteLearningMaterial_Success() throws Exception {
        doNothing().when(learningMaterialService).deleteLearningMaterial(1L);

        mockMvc.perform(delete("/api/learning-material/delete/1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // --- Improving Branch Coverage for Bulk Delete ---

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_LEARNING_MATERIAL")
    @DisplayName("DELETE /bulk-delete - Should return 400 when list is empty")
    void bulkDelete_EmptyList_BadRequest() throws Exception {
        mockMvc.perform(delete("/api/learning-material/bulk-delete")
                        .param("materialIds", "")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET - Should return all materials")
    void getAllLearningMaterials_Success() throws Exception {
        when(learningMaterialService.getAllLearningMaterials()).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/learning-material"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

}
