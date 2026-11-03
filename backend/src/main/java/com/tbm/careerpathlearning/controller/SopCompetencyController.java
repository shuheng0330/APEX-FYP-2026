package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.service.SopScoreboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Read-only SOP training results for the Staff Appraisal module (FR-10-06): a staff member's
 * completed SOP courses with their final competency scores. Staff may read their own; appraisers,
 * HR and trainers may read anyone's. The appraisal module reads this endpoint; it is not changed.
 */
@RestController
@RequestMapping("/api/sop-competency")
@PreAuthorize("hasAuthority(T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName())")
public class SopCompetencyController {

    private static final Set<String> READ_ANYONE = Set.of(
            AuthorityName.CAN_MANAGE_EVALUATION.getAuthorityName(),
            AuthorityName.CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName(),
            AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName(),
            AuthorityName.CAN_MANAGE_TRAINING.getAuthorityName());

    @Autowired
    private SopScoreboardService sopScoreboardService;

    @GetMapping("/{staffId}")
    public ResponseEntity<?> scores(@PathVariable UUID staffId, Authentication authentication) {
        boolean self = staffId.equals(UUID.fromString(authentication.getPrincipal().toString()));
        boolean readAnyone = authentication.getAuthorities().stream().anyMatch(a -> READ_ANYONE.contains(a.getAuthority()));
        if (!self && !readAnyone) {
            throw new ForbiddenRequestException("You can only view your own training scores.");
        }
        return ResponseEntity.ok(sopScoreboardService.competencyScores(staffId));
    }
}
