package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingCancelRequest;
import com.tbm.careerpathlearning.dto.TrainingWalkInRequest;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * A session's attendance (FR-14), recorded by its trainer and HR. The Super Admin may look. Each change answers with
 * the attendance as it now stands.
 */
@RestController
@RequestMapping("/api/trainings")
public class TrainingAttendanceController {

    @Autowired
    private TrainingAttendanceService attendance;

    @GetMapping("/{id}/attendance")
    public ResponseEntity<?> sheet(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(attendance.sheet(id, TrainingViewers.of(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PutMapping("/{id}/attendance")
    public ResponseEntity<?> record(@PathVariable Long id, @RequestBody TrainingAttendanceRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(attendance.record(id, request, TrainingViewers.of(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/attendance/walk-ins")
    public ResponseEntity<?> addWalkIn(@PathVariable Long id, @RequestBody TrainingWalkInRequest request,
                                       Authentication authentication) {
        return ResponseEntity.ok(attendance.addWalkIn(id, request.getStaffId(), request.getReason(),
                TrainingViewers.of(authentication)));
    }

    /** The reason only when HR submits it after the trainer's 7 days. */
    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/attendance/submit")
    public ResponseEntity<?> submit(@PathVariable Long id, @RequestBody(required = false) TrainingCancelRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(attendance.submit(id, request == null ? null : request.getReason(),
                TrainingViewers.of(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping(value = "/{id}/attendance/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addEvidence(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                         Authentication authentication) {
        return ResponseEntity.ok(attendance.addEvidence(id, file, TrainingViewers.of(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @DeleteMapping("/{id}/attendance/evidence/{evidenceId}")
    public ResponseEntity<?> removeEvidence(@PathVariable Long id, @PathVariable Long evidenceId,
                                            Authentication authentication) {
        return ResponseEntity.ok(attendance.removeEvidence(id, evidenceId, TrainingViewers.of(authentication)));
    }

    @GetMapping("/{id}/attendance/evidence/{evidenceId}")
    public ResponseEntity<Resource> evidence(@PathVariable Long id, @PathVariable Long evidenceId,
                                             Authentication authentication) {
        TrainingAttendanceService.EvidenceFile file = attendance.evidenceFile(id, evidenceId, TrainingViewers.of(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
}
