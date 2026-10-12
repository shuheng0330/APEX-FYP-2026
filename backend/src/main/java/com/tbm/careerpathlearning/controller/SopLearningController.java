package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.SopFeedbackRequest;
import com.tbm.careerpathlearning.dto.SopQuizSubmission;
import com.tbm.careerpathlearning.service.SopLearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The staff Learning Portal (FR-11). Every signed-in user may use it, but only for the courses
 * they are enrolled in; quizzes are marked here so answers never reach the browser early.
 */
@RestController
@RequestMapping("/api/sop-learning")
@PreAuthorize("hasAuthority(T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName())")
public class SopLearningController {

    @Autowired
    private SopLearningService sopLearningService;

    @GetMapping("/home")
    public ResponseEntity<?> home(Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.home(userId(authentication)));
    }

    @PostMapping("/courses/{id}/enrol")
    public ResponseEntity<?> enrol(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.enrol(id, userId(authentication)));
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<?> course(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.course(id, userId(authentication)));
    }

    @PostMapping("/courses/{id}/start")
    public ResponseEntity<?> start(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.start(id, userId(authentication)));
    }

    @PostMapping("/courses/{id}/feedback")
    public ResponseEntity<?> feedback(@PathVariable Long id, @RequestBody SopFeedbackRequest request,
                                      Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.feedback(id, request, userId(authentication)));
    }

    @GetMapping("/modules/{moduleId}/material")
    public ResponseEntity<?> material(@PathVariable Long moduleId, Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.material(moduleId, userId(authentication)));
    }

    @GetMapping("/modules/{moduleId}/quiz")
    public ResponseEntity<?> quiz(@PathVariable Long moduleId, Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.quiz(moduleId, userId(authentication)));
    }

    @PostMapping("/modules/{moduleId}/attempts")
    public ResponseEntity<?> submit(@PathVariable Long moduleId, @RequestBody SopQuizSubmission submission,
                                    Authentication authentication) {
        return ResponseEntity.ok(sopLearningService.submit(moduleId, submission, userId(authentication)));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
