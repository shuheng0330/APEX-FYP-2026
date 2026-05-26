package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/staff-profile")
@CrossOrigin
public class StaffProfileController {

    @Value("${file.upload-dir}")
    private String UPLOAD_DIR;

    @Autowired
    private StaffService staffService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private StaffProfileService staffProfileService;

    @Autowired
    private FileService fileService;

    private static final Logger logger = LoggerFactory.getLogger(StaffProfileController.class);

    private static final String PROFILE_DIR = "profiles";

    private static final String PROFILE_PIC_DIR = "profile_pic";

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String PROFILE_EDIT_OPERATION = "Update Profile";

    private static final String STAFF_PROFILE_EDIT_OK = "staff.profile.edit.ok.msg";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final List<String> ACCEPTED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".heic", ".webp");

    private static final List<String> ACCEPTED_MEME_FILE_TYPE = List.of("image/jpeg", "image/png", "image/heic", "image/webp");

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping
    public ResponseEntity<?> getProfileData(@RequestParam UUID staffId, Authentication authentication) {

        StaffProfileDto selectedStaff = staffProfileService.findById(staffId);
        selectedStaff.getStaff().setPassword(null);

        return ResponseEntity.ok(selectedStaff);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @PostMapping("/upload-profile-picture")
    @Transactional
    public ResponseEntity<?> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        if (file.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        long maxSizeBytes = MAX_FILE_SIZE_IN_MB * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String lowerCaseName = originalFilename.toLowerCase();
        boolean isValidExtension = ACCEPTED_IMAGE_EXTENSIONS.stream()
                .anyMatch(lowerCaseName::endsWith);

        if (!isValidExtension) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_MEME_FILE_TYPE.contains(contentType)) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Path uploadDir = Paths.get(UPLOAD_DIR, PROFILE_DIR, userId, PROFILE_PIC_DIR);
        if (Files.exists(uploadDir)) {
            try (Stream<Path> files = Files.list(uploadDir)) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete old profile picture: {}", path, e);
                    }
                });
            }
        } else {
            Files.createDirectories(uploadDir);
        }

        Path targetFile = uploadDir.resolve(originalFilename.trim());
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        String relativeFilePath = Paths.get(PROFILE_DIR, userId, PROFILE_PIC_DIR, originalFilename.trim())
                .toString()
                .replace("\\", "/");
        StaffProfileDto selectedProfile = staffProfileService.findById(userUUID);
        selectedProfile.setProfilePicturePath(relativeFilePath);
        selectedProfile.setUpdatedBy(userUUID);
        selectedProfile.setUpdatedAt(now);
        staffProfileService.updateProfilePicture(selectedProfile.getStaffId(), selectedProfile);

        String successMessage = messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault());
        return ResponseEntity.ok(Map.of(
                "message", successMessage
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @Transactional
    @GetMapping("/get-profile-picture")
    public ResponseEntity<Resource> getProfilePicture(@RequestParam UUID staffId, Authentication authentication) throws IOException {

        String relativePath = staffProfileService.findById(staffId).getProfilePicturePath();
        if (!validationService.isNullOrBlank(relativePath)) {
            logger.info(relativePath);
            Path filePath = fileService.getFilePath(relativePath);

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @PutMapping("/edit")
    @Transactional
    public ResponseEntity<?> update(@RequestBody StaffProfileDto req, Authentication authentication) throws Exception {
        if (req == null || req.getStaffId() == null || req.getStaff() == null || validationService.isNullOrBlank(req.getStaff().getEmail())
                || validationService.isNullOrBlank(req.getStaff().getName()) || validationService.isNullOrBlank(req.getContactNumber())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        StaffProfileDto selectedStaffProfile = staffProfileService.findById(userUUID);
        StaffDto selectedStaff = selectedStaffProfile.getStaff();

        if (!selectedStaff.getEmail().trim().equalsIgnoreCase(req.getStaff().getEmail().trim())) { //Email changed
            selectedStaff.setFirstLogin(true);
            emailService.sendAccountRegisteredEmail(req.getStaff().getEmail().trim(), Locale.getDefault());
        }

        selectedStaff.setName(req.getStaff().getName());
        selectedStaffProfile.setContactNumber(req.getContactNumber());
        selectedStaffProfile.setAbout(req.getAbout());

        staffService.update(selectedStaff.getId(), selectedStaff);

        staffProfileService.update(selectedStaffProfile.getStaffId(), selectedStaffProfile);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault())
        ));
    }

}


