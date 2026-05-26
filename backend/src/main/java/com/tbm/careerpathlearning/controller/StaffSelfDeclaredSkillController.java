package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.CreateStaffSelfDeclaredSkillRequest;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffSelfDeclaredSkillDto;
import com.tbm.careerpathlearning.enums.SkillProficiency;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/staff-self-declare-skill")
@CrossOrigin
public class StaffSelfDeclaredSkillController {

    @Autowired
    private StaffService staffService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private StaffSelfDeclaredSkillService staffSelfDeclaredSkillService;

    private static final Logger logger = LoggerFactory.getLogger(StaffSelfDeclaredSkillController.class);

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String PROFILE_EDIT_OPERATION = "Update Profile";

    private static final String STAFF_PROFILE_EDIT_OK = "staff.profile.edit.ok.msg";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    public ResponseEntity<?> overview(@RequestParam UUID staffId, Authentication authentication) {

        return ResponseEntity.ok(staffSelfDeclaredSkillService.findByStaffId(staffId));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @PostMapping()
    @Transactional
    public ResponseEntity<?> create(@RequestBody CreateStaffSelfDeclaredSkillRequest req, Authentication authentication) throws Exception {
        if (req == null || req.getStaffId() == null
                || validationService.isNullOrBlank(req.getSkill()) || req.getProficiency() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);

        StaffDto staff = staffService.findById(userUUID);

        StaffSelfDeclaredSkillDto toBeCreated = new StaffSelfDeclaredSkillDto(
                staff,
                req.getSkill().trim(),
                validationService.isNullOrBlank(req.getDescription()) ? null : req.getDescription().trim(),
                Objects.requireNonNull(SkillProficiency.fromProficiency(req.getProficiency()).orElse(null)),
                userUUID,
                now,
                userUUID,
                now
        );

        staffSelfDeclaredSkillService.create(toBeCreated);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @PutMapping("/edit")
    public ResponseEntity<?> update(@RequestBody CreateStaffSelfDeclaredSkillRequest req, Authentication authentication) {
        if (req == null || req.getId() == null || req.getStaffId() == null
                || validationService.isNullOrBlank(req.getSkill()) || req.getProficiency() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);

        StaffSelfDeclaredSkillDto toBeUpdated = staffSelfDeclaredSkillService.findById(req.getId());

        toBeUpdated.setSkill(req.getSkill().trim());
        toBeUpdated.setDescription(validationService.isNullOrBlank(req.getDescription()) ? null : req.getDescription().trim());
        toBeUpdated.setProficiency(Objects.requireNonNull(SkillProficiency.fromProficiency(req.getProficiency()).orElse(null)));
        toBeUpdated.setUpdatedAt(now);
        toBeUpdated.setUpdatedBy(userUUID);

        staffSelfDeclaredSkillService.update(req.getId(), toBeUpdated);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    public ResponseEntity<?> update(@RequestParam Long selectedId, Authentication authentication) {
        if (selectedId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        staffSelfDeclaredSkillService.delete(selectedId);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    public ResponseEntity<?> update(@RequestParam Set<Long> selectedIds, Authentication authentication) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{PROFILE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        staffSelfDeclaredSkillService.deleteAllById(selectedIds);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_PROFILE_EDIT_OK, null, Locale.getDefault())
        ));
    }
}