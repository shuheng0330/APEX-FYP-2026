package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopVisibilityRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.service.SopAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Assign Training (FR-09). Trainers (CAN_MANAGE_TRAINING) assign the courses they uploaded;
 * holders of CAN_ASSIGN_TRAINING, such as HR and Super Admin, may assign any course (FR-09-07).
 */
@RestController
@RequestMapping("/api/sop-assignments")
@PreAuthorize("""
        hasAnyAuthority(
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName(),
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_ASSIGN_TRAINING.getAuthorityName()
        )
        """)
public class SopAssignmentController {

    @Autowired
    private SopAssignmentService sopAssignmentService;

    @GetMapping("/courses")
    public ResponseEntity<?> courses(Authentication authentication) {
        return ResponseEntity.ok(sopAssignmentService.listCourses(userId(authentication), canAssignAny(authentication)));
    }

    @GetMapping("/audience")
    public ResponseEntity<?> audience() {
        return ResponseEntity.ok(sopAssignmentService.getAudience());
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody SopAssignmentRequest request) {
        return ResponseEntity.ok(sopAssignmentService.preview(request));
    }

    @PostMapping
    public ResponseEntity<?> assign(@RequestBody SopAssignmentRequest request, Authentication authentication) {
        return ResponseEntity.ok(sopAssignmentService.assign(request, userId(authentication), canAssignAny(authentication)));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam(required = false) Long sopDocumentId, Authentication authentication) {
        return ResponseEntity.ok(sopAssignmentService.history(sopDocumentId, userId(authentication), canAssignAny(authentication)));
    }

    @PutMapping("/courses/{id}/visibility")
    public ResponseEntity<?> setVisibility(@PathVariable Long id, @RequestBody SopVisibilityRequest request,
                                           Authentication authentication) {
        return ResponseEntity.ok(sopAssignmentService.setVisibility(id, request.getVisibility(),
                userId(authentication), canAssignAny(authentication)));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    private boolean canAssignAny(Authentication authentication) {
        String assignAny = AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName();
        return authentication.getAuthorities().stream().anyMatch(a -> assignAny.equals(a.getAuthority()));
    }
}
