package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
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
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/staff-cert")
@CrossOrigin
public class StaffCertController {

    @Value("${file.upload-dir}")
    private String UPLOAD_DIR;

    @Autowired
    private StaffService staffService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private StaffCertService staffCertService;

    private static final Logger logger = LoggerFactory.getLogger(StaffProfileController.class);

    private static final String PROFILE_DIR = "profiles";

    private static final String PROFILE_CERT_DIR = "cert";

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String PROFILE_EDIT_OPERATION = "Update Profile";

    private static final String STAFF_PROFILE_EDIT_OK = "staff.profile.edit.ok.msg";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final List<String> ACCEPTED_IMAGE_EXTENSIONS = List.of(".pdf");

    private static final List<String> ACCEPTED_MEME_FILE_TYPE = List.of("application/pdf");


    @PreAuthorize("""
                hasAnyAuthority(
                    T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
                )
            """)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> uploadCert(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        if (file.isEmpty() || validationService.isNullOrBlank(name)) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        long maxSizeBytes = MAX_FILE_SIZE_IN_MB * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String lowerCaseName = originalFileName.toLowerCase();
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

        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);

        Path uploadDir = Paths.get(UPLOAD_DIR, PROFILE_DIR, userId, PROFILE_CERT_DIR);
        Files.createDirectories(uploadDir);

        String safeFileName = name.trim() + "." + FilenameUtils.getExtension(originalFileName);
        Path targetFile = uploadDir.resolve(safeFileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        String relativeFilePath = Paths.get(PROFILE_DIR, userId, PROFILE_CERT_DIR, safeFileName)
                .toString()
                .replace("\\", "/");

        StaffDto staffDto = staffService.findById(userUUID);
        StaffCertDto staffCertDto = new StaffCertDto(
                staffDto,
                name.trim(),
                safeFileName,
                description == null || description.trim().isEmpty() ? null : description.trim(),
                relativeFilePath,
                userUUID,
                now,
                userUUID,
                now
        );

        staffCertService.create(staffCertDto);

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
    @GetMapping("/overview")
    @Transactional
    public ResponseEntity<?> uploadCert(
            @RequestParam UUID staffId,
            Authentication authentication) {
        return ResponseEntity.ok(staffCertService.findByStaffId(staffId).stream().peek(dto -> {
            dto.getStaff().setPassword(null);
            dto.setCertPath(null);
        }));
    }

    @PreAuthorize("""
                hasAnyAuthority(
                    T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
                )
            """)
    @GetMapping("/get-uploaded-cert")
    @Transactional
    public ResponseEntity<?> getUploadedCert(
            @RequestParam UUID staffId,
            @RequestParam String fileName,
            Authentication authentication) throws MalformedURLException {

        Path filePath = Paths.get(UPLOAD_DIR, PROFILE_DIR, staffId.toString(), "cert", fileName);

        if (!Files.exists(filePath)) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PreAuthorize("""
                hasAnyAuthority(
                    T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
                )
            """)
    @PutMapping("/edit")
    @Transactional
    public ResponseEntity<?> updateCert(
            @RequestBody StaffCertDto req,
            Authentication authentication
    ) throws IOException {
        if (req == null || req.getId() == null || validationService.isNullOrBlank(req.getCertName())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        StaffCertDto existing = staffCertService.findById(req.getId());

        String newCertName = req.getCertName().trim();
        String newFileName = newCertName.endsWith(".pdf") ? newCertName : newCertName + ".pdf";

        boolean nameChanged = !newCertName.equalsIgnoreCase(existing.getCertName());

        if (nameChanged) {
            Path oldFilePath = Paths.get(UPLOAD_DIR, PROFILE_DIR, existing.getStaff().getId().toString(), "cert", existing.getFileName());
            Path newFilePath = Paths.get(UPLOAD_DIR, PROFILE_DIR, existing.getStaff().getId().toString(), "cert", newFileName);


            if (!Files.exists(oldFilePath)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

            String relativeFilePath = Paths.get(PROFILE_DIR, existing.getStaff().getId().toString(), "cert", newFileName)
                    .toString()
                    .replace("\\", "/");

            existing.setCertName(newCertName);
            existing.setFileName(newFileName);
            existing.setCertPath(relativeFilePath);
        }

        existing.setDescription(validationService.isNullOrBlank(req.getDescription()) ? null : req.getDescription().trim());


        existing.setUpdatedBy(userUUID);
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8)));

        staffCertService.update(existing);

        String successMessage = messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault());
        return ResponseEntity.ok(Map.of("message", successMessage));
    }

    @PreAuthorize("""
                hasAnyAuthority(
                    T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
                )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> deleteCert(
            @RequestParam Long certId,
            Authentication authentication
    ) throws IOException {
        if (certId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        StaffCertDto deletedCert = staffCertService.findAndDeleteById(certId);

        // delete file in dir
        deleteFileInDirectory(deletedCert.getStaff().getId(), deletedCert.getFileName());

        String successMessage = messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault());
        return ResponseEntity.ok(Map.of("message", successMessage));
    }

    @PreAuthorize("""
                hasAnyAuthority(
                    T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
                )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> deleteAllCert(
            @RequestParam Set<Long> certIds,
            Authentication authentication
    ) throws IOException, RuntimeException {
        if (certIds == null || certIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<StaffCertDto> deletedCerts = staffCertService.findAndDeleteByIdIn(certIds);

        deletedCerts.forEach(deletedCert -> {
            try {
                deleteFileInDirectory(deletedCert.getStaff().getId(), deletedCert.getFileName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        String successMessage = messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault());
        return ResponseEntity.ok(Map.of("message", successMessage));
    }

    private void deleteFileInDirectory(UUID staffId, String fileName) throws IOException, RuntimeException {
        Path filePath = Paths.get(UPLOAD_DIR, PROFILE_DIR, staffId.toString(), "cert", fileName);
        Files.deleteIfExists(filePath);
    }
}


