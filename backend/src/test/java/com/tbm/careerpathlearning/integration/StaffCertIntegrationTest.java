package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.StaffCertDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffCert;
import com.tbm.careerpathlearning.repository.StaffCertRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class StaffCertIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffCertRepository staffCertRepository;

    // Temporary directory for file upload tests
    @TempDir
    static Path tempUploadDir;

    // Override the 'file.upload-dir' property to use our JUnit TempDir
    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> tempUploadDir.toAbsolutePath().toString());
    }

    private Staff testStaff;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        // 1. Create Staff
        testStaff = new Staff();
        UUID tempId = UUID.randomUUID();
        testStaff.setId(tempId);
        testStaff.setName("John Doe");
        testStaff.setEmail("john@tbm.com");
        testStaff.setAccountStatus(StaffAccountStatus.ACTIVE);
        testStaff.setDeleted(false);
        testStaff.setCreatedAt(OffsetDateTime.now());
        testStaff.setUpdatedAt(OffsetDateTime.now());
        testStaff = staffRepository.saveAndFlush(testStaff);

        this.staffId = testStaff.getId();

        // 2. Setup Security Context
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        staffId.toString(),
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    // --- Scenario 1: Upload Cert (Happy Path) ---
    @Test
    void uploadCert_ShouldSaveFileAndDbRecord() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "MyCert.pdf",
                "application/pdf",
                "%PDF-1.4 content".getBytes()
        );

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "Java Certification")
                        .param("description", "Oracle Java 17 Certified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verify DB
        List<StaffCert> certs = staffCertRepository.findAllByStaff_Id(staffId);
        assertThat(certs).hasSize(1);
        StaffCert savedCert = certs.get(0);
        assertThat(savedCert.getCertName()).isEqualTo("Java Certification");
        // Filename construction check: name + original ext
        assertThat(savedCert.getFileName()).isEqualTo("Java Certification.pdf");

        // Verify File System
        Path expectedPath = tempUploadDir.resolve("profiles")
                .resolve(staffId.toString())
                .resolve("cert")
                .resolve("Java Certification.pdf");
        assertThat(expectedPath).exists();
    }

    // --- Scenario 2: Upload Cert (Validation Fail - Not PDF) ---
    @Test
    void uploadCert_ShouldFail_WhenFileNotPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                "image-data".getBytes()
        );

        mockMvc.perform(multipart("/api/staff-cert")
                        .file(file)
                        .param("name", "Invalid Cert"))
                .andExpect(status().isBadRequest()); // Expect validation error
    }

    // --- Scenario 3: Get Overview (List Certs) ---
    @Test
    void overview_ShouldReturnCertList() throws Exception {
        // Pre-populate DB
        createCertInSystem("AWS Solutions Architect");

        mockMvc.perform(get("/api/staff-cert/overview")
                        .param("staffId", staffId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].certName").value("AWS Solutions Architect"))
                .andExpect(jsonPath("$[0].certPath").doesNotExist()); // Sensitive path usually hidden
    }

    // --- Scenario 4: Download Cert ---
    @Test
    void getUploadedCert_ShouldReturnFileContent() throws Exception {
        // 1. Manually create file on disk
        String fileName = "MyCert.pdf";
        Path certDir = tempUploadDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Path filePath = certDir.resolve(fileName);
        Files.write(filePath, "%PDF-1.4 test content".getBytes());

        // 2. Call endpoint
        mockMvc.perform(get("/api/staff-cert/get-uploaded-cert")
                        .param("staffId", staffId.toString())
                        .param("fileName", fileName))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\""));
    }

    // --- Scenario 5: Update Cert (Rename File) ---
    @Test
    void updateCert_ShouldRenameFileAndDbRecord() throws Exception {
        // 1. Setup existing cert
        String oldName = "OldName";
        StaffCert existing = createCertInSystem(oldName);

        // Ensure file exists physically for renaming logic
        Path certDir = tempUploadDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Files.write(certDir.resolve(oldName + ".pdf"), "content".getBytes());

        // 2. Request to Rename
        StaffCertDto req = new StaffCertDto();
        req.setId(existing.getId());
        req.setCertName("NewName");
        req.setDescription("Updated Desc");
        // StaffDto is needed if your DTO maps it back, but usually ID is enough for lookup
        // However, your controller uses existing.getStaff().getId(), so DB lookup handles it.

        mockMvc.perform(put("/api/staff-cert/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 3. Verify DB
        StaffCert updated = staffCertRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getCertName()).isEqualTo("NewName");
        assertThat(updated.getFileName()).isEqualTo("NewName.pdf");

        // 4. Verify File System (Old gone, New exists)
        assertThat(certDir.resolve("OldName.pdf")).doesNotExist();
        assertThat(certDir.resolve("NewName.pdf")).exists();
    }

    // --- Scenario 6: Delete Cert ---
    @Test
    void deleteCert_ShouldRemoveDbRecordAndFile() throws Exception {
        // 1. Setup
        String name = "ToDelete";
        StaffCert cert = createCertInSystem(name);

        Path certDir = tempUploadDir.resolve("profiles").resolve(staffId.toString()).resolve("cert");
        Files.createDirectories(certDir);
        Path filePath = certDir.resolve(name + ".pdf");
        Files.write(filePath, "content".getBytes());

        // 2. Delete
        mockMvc.perform(delete("/api/staff-cert/delete")
                        .param("certId", cert.getId().toString()))
                .andExpect(status().isOk());

        // 3. Verify
        assertThat(staffCertRepository.findById(cert.getId())).isEmpty();
        assertThat(filePath).doesNotExist();
    }

    // Helper to create DB record
    private StaffCert createCertInSystem(String name) {
        StaffCert cert = new StaffCert();
        cert.setStaff(testStaff);
        cert.setCertName(name);
        cert.setFileName(name + ".pdf");
        cert.setCertPath("profiles/" + staffId + "/cert/" + name + ".pdf");
        cert.setCreatedAt(OffsetDateTime.now());
        cert.setUpdatedAt(OffsetDateTime.now());
        cert.setCreatedBy(staffId);
        cert.setUpdatedBy(staffId);
        return staffCertRepository.saveAndFlush(cert);
    }
}