package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.service.SopScoreboardService;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The Training Scoreboard (FR-10). Trainers (CAN_MANAGE_TRAINING) see the staff on the courses they
 * uploaded; holders of CAN_ASSIGN_TRAINING, such as HR and Super Admin, see every course.
 */
@RestController
@RequestMapping("/api/sop-scoreboard")
@PreAuthorize("""
        hasAnyAuthority(
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName(),
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_ASSIGN_TRAINING.getAuthorityName(),
            T(com.tbm.careerpathlearning.service.TrainerAccessService).TEAM_TRAINING_AUTHORITY
        )
        """)
public class SopScoreboardController {

    @Autowired
    private SopScoreboardService sopScoreboardService;

    @GetMapping
    public ResponseEntity<?> scoreboard(Authentication authentication) {
        return ResponseEntity.ok(sopScoreboardService.scoreboard(userId(authentication), seeAll(authentication), team(authentication)));
    }

    @GetMapping("/enrollments/{id}")
    public ResponseEntity<?> enrollment(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sopScoreboardService.enrollment(id, userId(authentication), seeAll(authentication),
                team(authentication)));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    /** A manager: sees their direct reports' training too (FR-10-10). */
    private boolean team(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> TrainerAccessService.TEAM_TRAINING_AUTHORITY.equals(a.getAuthority()));
    }

    private boolean seeAll(Authentication authentication) {
        String assignAny = AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName();
        return authentication.getAuthorities().stream().anyMatch(a -> assignAny.equals(a.getAuthority()));
    }
}
