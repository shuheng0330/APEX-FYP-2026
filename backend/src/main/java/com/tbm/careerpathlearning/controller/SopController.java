package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.SopModuleEditRequest;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopRejectRequest;
import com.tbm.careerpathlearning.service.SopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Trainer-facing SOP endpoints: upload + AI generation (FR-08) and the
 * material/quiz review gates (FR-09 / FR-10). All actions require
 * CAN_MANAGE_TRAINING.
 */
@RestController
@RequestMapping("/api/sop")
@PreAuthorize("""
        hasAnyAuthority(
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName()
        )
        """)
public class SopController {

    @Autowired
    private SopService sopService;

    // --- Module 1: upload + generation --------------------------------------

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "version", required = false) String version,
                                    @RequestParam(value = "departmentTag", required = false) String departmentTag,
                                    Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        return ResponseEntity.ok(sopService.upload(file, title, version, departmentTag, userId));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(sopService.listDocuments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(sopService.getDetail(id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(sopService.getStatus(id));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<?> regenerate(@PathVariable Long id) {
        return ResponseEntity.ok(sopService.regenerateDocument(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        sopService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // --- Module 2: material review (FR-09) ----------------------------------

    @GetMapping("/module/{moduleId}")
    public ResponseEntity<?> getModule(@PathVariable Long moduleId) {
        return ResponseEntity.ok(sopService.getModule(moduleId));
    }

    @PutMapping("/module/{moduleId}")
    public ResponseEntity<?> updateModule(@PathVariable Long moduleId, @RequestBody SopModuleEditRequest req) {
        return ResponseEntity.ok(sopService.updateModule(moduleId, req.getTitle(), req.getContent()));
    }

    @PostMapping("/module/{moduleId}/approve")
    public ResponseEntity<?> approveModule(@PathVariable Long moduleId) {
        return ResponseEntity.ok(sopService.approveModule(moduleId));
    }

    @PostMapping("/module/{moduleId}/reject")
    public ResponseEntity<?> rejectModule(@PathVariable Long moduleId, @RequestBody SopRejectRequest req) {
        return ResponseEntity.ok(sopService.rejectModule(moduleId, req.getReason()));
    }

    // --- Module 2: quiz review (FR-10) --------------------------------------

    @PutMapping("/quiz/{questionId}")
    public ResponseEntity<?> updateQuestion(@PathVariable Long questionId, @RequestBody SopQuizQuestionDto dto) {
        return ResponseEntity.ok(sopService.updateQuestion(questionId, dto));
    }

    @PostMapping("/module/{moduleId}/quiz/approve")
    public ResponseEntity<?> approveQuiz(@PathVariable Long moduleId) {
        return ResponseEntity.ok(sopService.approveModuleQuiz(moduleId));
    }

    @PostMapping("/module/{moduleId}/quiz/reject")
    public ResponseEntity<?> rejectQuiz(@PathVariable Long moduleId, @RequestBody SopRejectRequest req) {
        return ResponseEntity.ok(sopService.rejectModuleQuiz(moduleId, req.getReason()));
    }
}
