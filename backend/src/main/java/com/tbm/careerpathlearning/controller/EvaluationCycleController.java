package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.EvaluationCycleDto;
import com.tbm.careerpathlearning.service.EvaluationCycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/evaluation-cycle")
@CrossOrigin

public class EvaluationCycleController {

    @Autowired
    private EvaluationCycleService evaluationCycleService;

    @GetMapping("/current")
    public ResponseEntity<EvaluationCycleDto> getCurrentCycle() {
        EvaluationCycleDto current = evaluationCycleService.getCurrentCycle();
        return ResponseEntity.ok(current); // returns null if no OPEN cycle
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @PostMapping
    public ResponseEntity<EvaluationCycleDto> openNewCycle(
            @RequestBody EvaluationCycleDto dto, Authentication authentication) {

        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        evaluationCycleService.openNewCycle(dto, userUUID);

        EvaluationCycleDto current = evaluationCycleService.getCurrentCycle();
        return ResponseEntity.status(HttpStatus.CREATED).body(current);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @PutMapping("/update/{id}")
    public ResponseEntity<EvaluationCycleDto> updateCycle(@PathVariable Long id, @RequestBody EvaluationCycleDto dto, Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        EvaluationCycleDto updated =
                evaluationCycleService.updateEvaluationCycle(id, dto, userUUID);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/history")
    public List<EvaluationCycleDto> history() {
        return evaluationCycleService.getEvaluationCycleHistory();
    }



}
