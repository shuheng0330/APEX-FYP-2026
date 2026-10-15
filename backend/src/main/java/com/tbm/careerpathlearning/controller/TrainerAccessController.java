package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Manage Trainer Access (UC-43). Only the Super Admin, the holder of CAN_MANAGE_ACCESS_CONTROL, grants and
 * revokes Trainer access for individual staff (FR-20-01, FR-20-07).
 */
@RestController
@RequestMapping("/api/trainer-access")
@PreAuthorize("""
        hasAuthority(T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName())
        """)
public class TrainerAccessController {

    @Autowired
    private TrainerAccessService trainerAccessService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(trainerAccessService.list());
    }

    @PostMapping("/{staffId}/grant")
    public ResponseEntity<?> grant(@PathVariable UUID staffId, @RequestBody TrainerAccessRequest request,
                                   Authentication authentication) {
        return ResponseEntity.ok(trainerAccessService.grant(staffId, request, userId(authentication)));
    }

    @PostMapping("/{staffId}/revoke")
    public ResponseEntity<?> revoke(@PathVariable UUID staffId, @RequestBody TrainerAccessRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(trainerAccessService.revoke(staffId, request, userId(authentication)));
    }

    @GetMapping("/{staffId}/history")
    public ResponseEntity<?> history(@PathVariable UUID staffId) {
        return ResponseEntity.ok(trainerAccessService.history(staffId));
    }

    @GetMapping("/{staffId}/courses")
    public ResponseEntity<?> courses(@PathVariable UUID staffId) {
        return ResponseEntity.ok(trainerAccessService.ownedCourses(staffId));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
