package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffProfileDto;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffProfile;
import com.tbm.careerpathlearning.repository.StaffProfileRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class StaffProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffProfileRepository staffProfileRepository;

    @MockitoBean
    private EmailService emailService;

    // Temporary directory for file upload tests to avoid polluting the real OS file system
    @TempDir
    static Path tempUploadDir;

    // Override the 'file.upload-dir' property to use our JUnit TempDir
    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> tempUploadDir.toAbsolutePath().toString());
    }

    private Staff testStaff;
    private StaffProfile testProfile;
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

        testProfile = new StaffProfile();
        testProfile.setStaff(testStaff);
        testProfile.setContactNumber("0123456789");
        testProfile.setAbout("I am a software engineer.");
        testProfile.setCreatedAt(OffsetDateTime.now());
        testProfile.setUpdatedAt(OffsetDateTime.now());

        staffProfileRepository.saveAndFlush(testProfile);
    }

    // --- Scenario 1: Get Profile Data ---
    @Test
    void getProfileData_ShouldReturnDetails_WhenAuthorized() throws Exception {
        mockMvc.perform(get("/api/staff-profile")
                        .param("staffId", staffId.toString())
                        .with(user(staffId.toString()).authorities(() -> "ROLE_USER"))) // Mock Login
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactNumber").value("0123456789"))
                .andExpect(jsonPath("$.staff.name").value("John Doe"))
                .andExpect(jsonPath("$.staff.password").doesNotExist()); // Security check
    }

    // --- Scenario 2: Edit Profile (Update Contact & Name) ---
    @Test
    void updateProfile_ShouldUpdateDB_WhenValid() throws Exception {
        // Prepare Request DTO
        StaffProfileDto req = new StaffProfileDto();
        req.setStaffId(staffId);
        req.setContactNumber("0199999999");
        req.setAbout("Updated Bio");

        StaffDto staffDto = new StaffDto();
        staffDto.setId(staffId);
        staffDto.setName("John Updated"); // Name change
        staffDto.setEmail("john@tbm.com"); // Email same
        req.setStaff(staffDto);

        mockMvc.perform(put("/api/staff-profile/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(staffId.toString()).authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());

        // Verify DB
        Staff updatedStaff = staffRepository.findById(staffId).orElseThrow();
        StaffProfile updatedProfile = staffProfileRepository.findById(staffId).orElseThrow();

        assertThat(updatedStaff.getName()).isEqualTo("John Updated");
        assertThat(updatedProfile.getContactNumber()).isEqualTo("0199999999");
        assertThat(updatedProfile.getAbout()).isEqualTo("Updated Bio");
    }

    // --- Scenario 3: Email Change Triggers Re-verification ---
    @Test
    void updateProfile_ShouldTriggerEmail_WhenEmailChanged() throws Exception {
        // Prepare Request
        StaffProfileDto req = new StaffProfileDto();
        req.setStaffId(staffId);
        req.setContactNumber("0123456789");
        req.setAbout("Bio");

        StaffDto staffDto = new StaffDto();
        staffDto.setId(staffId);
        staffDto.setName("John Doe");
        staffDto.setEmail("newemail@tbm.com"); // <--- Changed Email
        req.setStaff(staffDto);

        mockMvc.perform(put("/api/staff-profile/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(user(staffId.toString()).authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());

        // Verify Email Service Triggered
        verify(emailService).sendAccountRegisteredEmail(eq("newemail@tbm.com"), any(Locale.class));

        // Verify First Login Flag Reset
        Staff updatedStaff = staffRepository.findById(staffId).orElseThrow();
        assertThat(updatedStaff.isFirstLogin()).isTrue(); // Should force re-login/reset
    }

    // --- Scenario 4: Upload Profile Picture ---
    @Test
    void uploadProfilePicture_ShouldSaveFileAndUpdatePath() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "some-image-data".getBytes()
        );

        mockMvc.perform(multipart("/api/staff-profile/upload-profile-picture")
                        .file(file)
                        .with(user(staffId.toString()).authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());

        // Verify DB Update
        StaffProfile updatedProfile = staffProfileRepository.findById(staffId).orElseThrow();
        assertThat(updatedProfile.getProfilePicturePath()).contains("avatar.png");
        assertThat(updatedProfile.getUpdatedAt()).isNotNull();

        // Verify File System (Optional, but good for SIT)
        // Checks if file exists in the temp directory structure
        Path expectedPath = tempUploadDir.resolve("profiles").resolve(staffId.toString()).resolve("profile_pic").resolve("avatar.png");
        assertThat(expectedPath).exists();
    }

    // --- Scenario 5: Upload Invalid File (Validation Test) ---
    @Test
    void uploadProfilePicture_ShouldFail_WhenFileIsText() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "malicious.txt",
                "text/plain",
                "hacking-script".getBytes()
        );

        mockMvc.perform(multipart("/api/staff-profile/upload-profile-picture")
                        .file(txtFile)
                        .with(user(staffId.toString()).authorities(() -> "ROLE_USER")))
                .andExpect(status().isBadRequest()); // Expect validation error
    }

    // --- Scenario 6: Get Profile Picture ---
    @Test
    void getProfilePicture_ShouldReturnImage_WhenExists() throws Exception {
        // 1. Manually setup a file on disk (simulate previous upload)
        Path userPicDir = tempUploadDir.resolve("profiles").resolve(staffId.toString()).resolve("profile_pic");
        java.nio.file.Files.createDirectories(userPicDir);
        Path imagePath = userPicDir.resolve("existing.jpg");
        java.nio.file.Files.write(imagePath, new byte[]{1, 2, 3});

        // 2. Update DB to point to this file
        // Note: The controller logic constructs path via Paths.get(PROFILE_DIR, userId...)
        // So we just need to set the DB path to something non-null usually,
        // OR the controller might construct the absolute path dynamically.
        // Looking at controller: String relativeFilePath = ... "profiles/UUID/profile_pic/filename"
        String relativeDbPath = "profiles/" + staffId.toString() + "/profile_pic/existing.jpg";

        testProfile.setProfilePicturePath(relativeDbPath);
        staffProfileRepository.save(testProfile);

        mockMvc.perform(get("/api/staff-profile/get-profile-picture")
                        .param("staffId", staffId.toString())
                        .with(user(staffId.toString()).authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)) // Tika probes content type, might be octet-stream if just bytes
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"existing.jpg\""));
    }
}