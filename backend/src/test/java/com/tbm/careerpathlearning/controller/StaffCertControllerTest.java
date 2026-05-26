package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.StaffCertDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.util.ReflectionTestUtils;

@WebMvcTest(controllers = StaffCertController.class, properties = {
        "file.upload-dir=src/test/resources/temp-uploads"
})
@AutoConfigureMockMvc(addFilters = false)
class StaffCertControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StaffCertController staffCertController;

    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private MessageSource messageSource;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private StaffCertService staffCertService;
    @MockitoBean
    private TokenService tokenService; // For Security Filter

    private UUID staffId;
    private StaffCertDto mockCertDto;

    // Create a temporary directory for each test run
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        StaffDto staff = new StaffDto();
        staff.setId(staffId);

        mockCertDto = new StaffCertDto();
        mockCertDto.setId(1L);
        mockCertDto.setStaff(staff);
        mockCertDto.setCertName("Test Cert");
        mockCertDto.setFileName("Test Cert.pdf");
        mockCertDto.setCertPath("path/to/cert");

        when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
        ReflectionTestUtils.setField(staffCertController, "UPLOAD_DIR", tempDir.toString());
    }

    // --- Upload Cert Tests ---

    @Test
    void uploadCert_ShouldReturn200_WhenFileIsValid() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(staffId.toString());

        when(validationService.isNullOrBlank("My Cert")).thenReturn(false);
        when(staffService.findById(staffId)).thenReturn(new StaffDto());

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "My Cert")
                        .principal(auth))
                .andExpect(status().isOk());

        verify(staffCertService).create(any(StaffCertDto.class));
    }

    @Test
    void uploadCert_ShouldReturn400_WhenNotPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "My Cert")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCert_ShouldReturn400_WhenFileTooLarge() throws Exception {
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", "content".getBytes()) {
            @Override
            public long getSize() {
                return 6 * 1024 * 1024;
            } // 6MB
        };

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(largeFile)
                        .param("name", "My Cert")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCert_ShouldReturn400_WhenNameIsBlank() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        when(validationService.isNullOrBlank("")).thenReturn(true);

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "") // Blank name
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCert_ShouldReturn400_WhenFileIsEmpty() throws Exception {
        // Create file with 0 bytes
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(emptyFile)
                        .param("name", "Valid Name")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCert_ShouldReturn400_WhenOriginalFilenameIsNull() throws Exception {
        // Filename is explicitly null
        MockMultipartFile file = new MockMultipartFile("file", null, "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "Valid Name")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCert_ShouldReturn400_WhenExtensionNotAllowed() throws Exception {
        // .exe is not in ACCEPTED_IMAGE_EXTENSIONS
        MockMultipartFile file = new MockMultipartFile("file", "test.exe", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "Valid Name")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    // --- Overview Tests ---

    @Test
    void overview_ShouldReturnList() throws Exception {
        when(staffCertService.findByStaffId(staffId)).thenReturn(List.of(mockCertDto));

        mockMvc.perform(get("/api/staff-cert/overview")
                        .param("staffId", staffId.toString())
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk());
    }

    // --- Get Uploaded Cert (Download) Tests ---

    @Test
    void getUploadedCert_ShouldReturnFile_WhenExists() throws Exception {

        // Setup file structure in TempDir
        Path certDir = tempDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Path pdfFile = certDir.resolve("test.pdf");
        Files.write(pdfFile, "pdf-content".getBytes());

        mockMvc.perform(get("/api/staff-cert/get-uploaded-cert")
                        .param("staffId", staffId.toString())
                        .param("fileName", "test.pdf")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void getUploadedCert_ShouldReturn400_WhenFileDoesNotExist() throws Exception {
        // Do NOT create file in tempDir
        mockMvc.perform(get("/api/staff-cert/get-uploaded-cert")
                        .param("staffId", staffId.toString())
                        .param("fileName", "missing.pdf")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    // --- Edit (Rename) Tests ---

    @Test
    void updateCert_ShouldRenameFile_WhenNameChanged() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(staffId.toString()); // Controller uses auth.getName() for update

        // 1. Setup existing file on disk
        Path certDir = tempDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Path oldFile = certDir.resolve("OldName.pdf");
        Files.write(oldFile, "content".getBytes());

        // 2. Mock existing DB record
        mockCertDto.setCertName("OldName");
        mockCertDto.setFileName("OldName.pdf");
        when(staffCertService.findById(1L)).thenReturn(mockCertDto);
        when(validationService.isNullOrBlank("NewName")).thenReturn(false);

        // 3. Request
        StaffCertDto req = new StaffCertDto();
        req.setId(1L);
        req.setCertName("NewName"); // Change Name
        req.setDescription("Desc");

        mockMvc.perform(put("/api/staff-cert/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(auth))
                .andExpect(status().isOk());

        // 4. Verification
        // Verify file was moved on disk
        assertFalse(Files.exists(oldFile));
        assertTrue(Files.exists(certDir.resolve("NewName.pdf")));

        verify(staffCertService).update(any(StaffCertDto.class));
    }

    @Test
    void updateCert_ShouldReturn400_WhenIdIsMissing() throws Exception {
        StaffCertDto req = new StaffCertDto();
        req.setId(null); // Missing ID
        req.setCertName("Valid Name");

        mockMvc.perform(put("/api/staff-cert/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCert_ShouldReturn400_WhenNameIsMissing() throws Exception {
        StaffCertDto req = new StaffCertDto();
        req.setId(1L);
        req.setCertName(""); // Blank Name
        when(validationService.isNullOrBlank("")).thenReturn(true);

        mockMvc.perform(put("/api/staff-cert/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCert_ShouldReturn400_WhenOldFileDoesNotExistDuringRename() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(staffId.toString());

        // Mock existing record
        mockCertDto.setCertName("OldName");
        mockCertDto.setFileName("OldName.pdf");
        when(staffCertService.findById(1L)).thenReturn(mockCertDto);

        // Request a NAME CHANGE (triggers rename logic)
        StaffCertDto req = new StaffCertDto();
        req.setId(1L);
        req.setCertName("NewName");

        // CRITICAL: We do NOT create the file on disk in tempDir.
        // The controller checks if (!Files.exists(oldFilePath)) -> throws BadRequest

        mockMvc.perform(put("/api/staff-cert/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .principal(auth))
                .andExpect(status().isBadRequest());
    }

    // --- Delete Tests ---

    @Test
    void deleteCert_ShouldRemoveFile() throws Exception {
        // 1. Setup file
        Path certDir = tempDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Path file = certDir.resolve("test.pdf");
        Files.write(file, "content".getBytes());

        // 2. Mock Service returning the DTO so controller knows what file to delete
        when(staffCertService.findAndDeleteById(1L)).thenReturn(mockCertDto);
        mockCertDto.setFileName("test.pdf");

        mockMvc.perform(delete("/api/staff-cert/delete")
                        .param("certId", "1")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk());

        // 3. Verify File Gone
        assertFalse(Files.exists(file));
    }

    @Test
    void deleteCert_ShouldReturn400_WhenIdIsMissing() throws Exception {
        // Sending request without 'certId' param
        mockMvc.perform(delete("/api/staff-cert/delete")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDelete_ShouldReturn400_WhenListIsEmpty() throws Exception {
        mockMvc.perform(delete("/api/staff-cert/bulk-delete")
                        .param("certIds", "") // Empty param
                        .principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDelete_ShouldRemoveFiles() throws Exception {
        // 1. Setup file
        Path certDir = tempDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Path file = certDir.resolve("test.pdf");
        Files.write(file, "content".getBytes());

        // 2. Mock Service
        mockCertDto.setFileName("test.pdf");
        when(staffCertService.findAndDeleteByIdIn(any(Set.class))).thenReturn(List.of(mockCertDto));

        mockMvc.perform(delete("/api/staff-cert/bulk-delete")
                        .param("certIds", "1")
                        .principal(mock(Authentication.class)))
                .andExpect(status().isOk());

        // 3. Verify File Gone
        assertFalse(Files.exists(file));
    }
}