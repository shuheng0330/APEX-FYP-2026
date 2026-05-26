package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffProfileDto;
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
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StaffProfileController.class, properties = {
        "file.upload-dir=src/test/resources/temp-uploads"
})
@AutoConfigureMockMvc(addFilters = false)
class StaffProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private StaffService staffService;
    @MockitoBean private MessageSource messageSource;
    @MockitoBean private EmailService emailService;
    @MockitoBean private ValidationService validationService;
    @MockitoBean private StaffProfileService staffProfileService;
    @MockitoBean private FileService fileService;
    @MockitoBean private TokenService tokenService;

    private StaffProfileDto mockProfileDto;
    private UUID staffId;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        mockProfileDto = new StaffProfileDto();
        mockProfileDto.setStaffId(staffId);
        mockProfileDto.setContactNumber("123");
        StaffDto staff = new StaffDto();
        staff.setId(staffId);
        staff.setEmail("staff@test.com");
        staff.setName("John");
        mockProfileDto.setStaff(staff);

        when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Message");
    }

    // 1. Get Profile
    @Test
    void getProfileData_ShouldReturn200() throws Exception {
        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);
        mockMvc.perform(get("/api/staff-profile").param("staffId", staffId.toString()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // 2. Upload Picture (Success)
    @Test
    void uploadProfilePicture_ShouldReturn200_WhenFileIsValid() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "bytes".getBytes());
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(staffId.toString());
        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);

        mockMvc.perform(multipart("/api/staff-profile/upload-profile-picture").file(file).principal(auth))
                .andExpect(status().isOk());
    }

    // 3. Upload Picture (Fail - Invalid Type)
    @Test
    void uploadProfilePicture_ShouldReturn400_WhenFileIsText() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "bytes".getBytes());
        mockMvc.perform(multipart("/api/staff-profile/upload-profile-picture").file(file).principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    // 4. Upload Picture (Fail - Too Large)
    @Test
    void uploadProfilePicture_ShouldReturn400_WhenFileTooLarge() throws Exception {
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.png", "image/png", "content".getBytes()) {
            @Override public long getSize() { return 6 * 1024 * 1024; } // 6MB
        };
        mockMvc.perform(multipart("/api/staff-profile/upload-profile-picture").file(largeFile).principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }

    // 5. Update Profile (Success)
    @Test
    void update_ShouldReturn200_WhenValid() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(staffId.toString());
        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        mockMvc.perform(put("/api/staff-profile/edit").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockProfileDto)))
                .andExpect(status().isOk());
    }

    // 6. Update Profile (Success - Email Trigger)
    @Test
    void update_ShouldTriggerEmail_WhenEmailChanged() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(staffId.toString());

        StaffDto dbStaff = new StaffDto();
        dbStaff.setEmail("old@test.com");
        dbStaff.setId(staffId);
        mockProfileDto.setStaff(dbStaff); // DB has Old

        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);

        StaffProfileDto reqDto = new StaffProfileDto();
        reqDto.setStaffId(staffId);
        reqDto.setContactNumber("123");
        StaffDto reqStaff = new StaffDto();
        reqStaff.setName("John");
        reqStaff.setEmail("new@test.com"); // Req has New
        reqDto.setStaff(reqStaff);

        mockMvc.perform(put("/api/staff-profile/edit").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isOk());

        verify(emailService).sendAccountRegisteredEmail(eq("new@test.com"), any());
    }

    // 7. Update Profile (Fail - Validation)
    @Test
    void update_ShouldThrowError_WhenNameEmpty() throws Exception {
        mockProfileDto.getStaff().setName("");
        when(validationService.isNullOrBlank("")).thenReturn(true);
        mockMvc.perform(put("/api/staff-profile/edit").principal(mock(Authentication.class)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockProfileDto)))
                .andExpect(status().isBadRequest());
    }

    // 8. Get Picture (Success)
    @Test
    void getProfilePicture_ShouldReturnFile_WhenExists() throws Exception {
        String path = "profiles/u1/pic.png";
        mockProfileDto.setProfilePicturePath(path);
        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);
        when(validationService.isNullOrBlank(path)).thenReturn(false);

        Path tempFile = tempDir.resolve("pic.png");
        Files.createFile(tempFile);
        Files.write(tempFile, "img".getBytes());
        when(fileService.getFilePath(path)).thenReturn(tempFile);

        mockMvc.perform(get("/api/staff-profile/get-profile-picture").param("staffId", staffId.toString()).principal(mock(Authentication.class)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    // 9. Get Picture (Fail - No Picture Set)
    @Test
    void getProfilePicture_ShouldReturn204_WhenNoPictureSet() throws Exception {
        mockProfileDto.setProfilePicturePath(null);
        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);
        when(validationService.isNullOrBlank(null)).thenReturn(true);

        mockMvc.perform(get("/api/staff-profile/get-profile-picture").param("staffId", staffId.toString()).principal(mock(Authentication.class)))
                .andExpect(status().isNoContent());
    }

    // 10. Get Picture (Fail - File Not Found on Disk)
    @Test
    void getProfilePicture_ShouldReturn400_WhenFileMissing() throws Exception {
        String path = "profiles/u1/pic.png";
        mockProfileDto.setProfilePicturePath(path);
        when(staffProfileService.findById(staffId)).thenReturn(mockProfileDto);
        when(validationService.isNullOrBlank(path)).thenReturn(false);

        when(fileService.getFilePath(path)).thenReturn(tempDir.resolve("missing.png"));

        mockMvc.perform(get("/api/staff-profile/get-profile-picture").param("staffId", staffId.toString()).principal(mock(Authentication.class)))
                .andExpect(status().isBadRequest());
    }
}