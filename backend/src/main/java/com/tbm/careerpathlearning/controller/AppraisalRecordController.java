package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.AppraisalReadinessDto;
import com.tbm.careerpathlearning.dto.AppraisalRecordDto;
import com.tbm.careerpathlearning.dto.HrAppraisalActionDto;
import com.tbm.careerpathlearning.service.AppraisalRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appraisal")
@CrossOrigin
public class AppraisalRecordController {

    @Autowired
    private AppraisalRecordService appraisalRecordService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION.getAuthorityName()
            )
            """)
    @PostMapping
    public ResponseEntity<AppraisalRecordDto> createOrUpdateDraft(
            @RequestBody AppraisalRecordDto dto,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        AppraisalRecordDto saved = appraisalRecordService.createOrUpdateDraft(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION.getAuthorityName()
            )
            """)
    @PutMapping("/{id}/submit")
    public ResponseEntity<AppraisalRecordDto> submit(@PathVariable UUID id, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        AppraisalRecordDto submitted = appraisalRecordService.submit(id, userId);
        return ResponseEntity.ok(submitted);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION.getAuthorityName()
            )
            """)
    @GetMapping("/by-staff/{staffId}")
    public ResponseEntity<List<AppraisalRecordDto>> getByStaff(@PathVariable UUID staffId) {
        return ResponseEntity.ok(appraisalRecordService.getAllByStaffId(staffId));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION.getAuthorityName()
            )
            """)
    @GetMapping("/readiness-score")
    public ResponseEntity<AppraisalReadinessDto> getReadinessScore(
            @RequestParam UUID staffId,
            @RequestParam Long evaluationCycleId,
            @RequestParam Integer reviewPeriodYears
    ) {
        AppraisalReadinessDto readinessScore = appraisalRecordService.calculateReadinessScore(
                staffId,
                evaluationCycleId,
                reviewPeriodYears
        );
        return ResponseEntity.ok(readinessScore);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/pending")
    public ResponseEntity<List<AppraisalRecordDto>> getPendingRecords() {
        return ResponseEntity.ok(appraisalRecordService.getPendingRecords());
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @PutMapping("/{id}/approve")
    public ResponseEntity<AppraisalRecordDto> approve(@PathVariable UUID id, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        AppraisalRecordDto approved = appraisalRecordService.approve(id, userId);
        return ResponseEntity.ok(approved);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @PutMapping("/{id}/override-approve")
    public ResponseEntity<AppraisalRecordDto> overrideAndApprove(
            @PathVariable UUID id,
            @RequestBody HrAppraisalActionDto dto,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        AppraisalRecordDto approved = appraisalRecordService.overrideAndApprove(id, dto, userId);
        return ResponseEntity.ok(approved);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @PutMapping("/{id}/return")
    public ResponseEntity<AppraisalRecordDto> returnForRevision(
            @PathVariable UUID id,
            @RequestBody HrAppraisalActionDto dto,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        AppraisalRecordDto returned = appraisalRecordService.returnForRevision(id, dto, userId);
        return ResponseEntity.ok(returned);
    }
}
