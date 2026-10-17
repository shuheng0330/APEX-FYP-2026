package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.SopCourseSettingsRequest;
import com.tbm.careerpathlearning.dto.SopModuleEditRequest;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopRejectRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.service.SopService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SOP endpoints: upload + AI generation (FR-08) and the material/quiz review gates (FR-09 / FR-10).
 * Trainers (CAN_MANAGE_TRAINING) work on the courses they own; HR (CAN_ASSIGN_TRAINING) and the Super Admin
 * (CAN_MANAGE_ACCESS_CONTROL) may look at every course read-only (FR-07-08, FR-07-19). Changes need
 * CAN_MANAGE_TRAINING, and the service lets each trainer change only their own courses.
 */
@RestController
@RequestMapping("/api/sop")
@PreAuthorize(SopController.VIEW)
public class SopController {

    static final String VIEW = """
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_ASSIGN_TRAINING.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """;

    static final String CHANGE = """
            hasAuthority(T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName())
            """;

    @Autowired
    private SopService sopService;

    // --- Module 1: upload + generation --------------------------------------

    @PostMapping("/upload")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "version", required = false) String version,
                                    @RequestParam(value = "departmentTag", required = false) String departmentTag,
                                    Authentication authentication) {
        return ResponseEntity.ok(sopService.upload(file, title, version, departmentTag, userId(authentication)));
    }

    /** @param mine only the signed-in person's own courses, as on the upload page */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "mine", defaultValue = "false") boolean mine,
                                  Authentication authentication) {
        return ResponseEntity.ok(sopService.listDocuments(viewer(authentication), mine));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopService.getDetail(id, viewer(authentication)));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopService.getStatus(id, viewer(authentication)));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> regenerate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopService.regenerateDocument(id, viewer(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        sopService.deleteDocument(id, viewer(authentication));
        return ResponseEntity.noContent().build();
    }

    /** The course's own pass mark and estimated duration (FR-07-15); its owner only. */
    @PutMapping("/{id}/settings")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> updateSettings(@PathVariable Long id, @RequestBody SopCourseSettingsRequest request,
                                            Authentication authentication) {
        return ResponseEntity.ok(sopService.updateSettings(id, request, viewer(authentication)));
    }

    // --- Module 2: material review (FR-09) ----------------------------------

    @GetMapping("/module/{moduleId}")
    public ResponseEntity<?> getModule(@PathVariable Long moduleId, Authentication authentication) {
        return ResponseEntity.ok(sopService.getModule(moduleId, viewer(authentication)));
    }

    @PutMapping("/module/{moduleId}")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> updateModule(@PathVariable Long moduleId, @RequestBody SopModuleEditRequest req,
                                          Authentication authentication) {
        return ResponseEntity.ok(sopService.updateModule(moduleId, req.getTitle(), req.getContent(), viewer(authentication)));
    }

    @PostMapping("/module/{moduleId}/approve")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> approveModule(@PathVariable Long moduleId, Authentication authentication) {
        return ResponseEntity.ok(sopService.approveModule(moduleId, viewer(authentication)));
    }

    @PostMapping("/module/{moduleId}/reject")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> rejectModule(@PathVariable Long moduleId, @RequestBody SopRejectRequest req,
                                          Authentication authentication) {
        return ResponseEntity.ok(sopService.rejectModule(moduleId, req.getReason(), viewer(authentication)));
    }

    // --- Module 2: quiz review (FR-10) --------------------------------------

    @PutMapping("/quiz/{questionId}")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> updateQuestion(@PathVariable Long questionId, @RequestBody SopQuizQuestionDto dto,
                                            Authentication authentication) {
        return ResponseEntity.ok(sopService.updateQuestion(questionId, dto, viewer(authentication)));
    }

    @PostMapping("/module/{moduleId}/quiz")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> addQuestion(@PathVariable Long moduleId, @RequestBody SopQuizQuestionDto dto,
                                         Authentication authentication) {
        return ResponseEntity.ok(sopService.addQuestion(moduleId, dto, viewer(authentication)));
    }

    @PostMapping("/module/{moduleId}/quiz/approve")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> approveQuiz(@PathVariable Long moduleId, Authentication authentication) {
        return ResponseEntity.ok(sopService.approveModuleQuiz(moduleId, viewer(authentication)));
    }

    @PostMapping("/module/{moduleId}/quiz/reject")
    @PreAuthorize(CHANGE)
    public ResponseEntity<?> rejectQuiz(@PathVariable Long moduleId, @RequestBody SopRejectRequest req,
                                        Authentication authentication) {
        return ResponseEntity.ok(sopService.rejectModuleQuiz(moduleId, req.getReason(), viewer(authentication)));
    }

    @GetMapping("/module/{moduleId}/quiz/history")
    public ResponseEntity<?> quizHistory(@PathVariable Long moduleId, Authentication authentication) {
        return ResponseEntity.ok(sopService.getQuizHistory(moduleId, viewer(authentication)));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    private SopViewer viewer(Authentication authentication) {
        Set<String> held = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean seesAllCourses = held.contains(AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName())
                || held.contains(AuthorityName.CAN_MANAGE_ACCESS_CONTROL.getAuthorityName());
        return new SopViewer(userId(authentication), seesAllCourses);
    }
}
