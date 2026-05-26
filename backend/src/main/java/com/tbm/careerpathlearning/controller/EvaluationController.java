package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.EvaluationDTO;
import com.tbm.careerpathlearning.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/evaluation")
@CrossOrigin
public class EvaluationController {

    @Autowired
    private EvaluationService evaluationService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION.getAuthorityName()
            )
            """)
    @PostMapping
    public ResponseEntity<EvaluationDTO> createEvaluation(@RequestBody EvaluationDTO evaluation) {
        EvaluationDTO createdEvaluation = evaluationService.createEvaluation(evaluation);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvaluation);
    }

    @GetMapping
    public ResponseEntity<List<EvaluationDTO>> getEvaluations() {
        List<EvaluationDTO> evaluations = evaluationService.getAllEvaluations();
        return ResponseEntity.ok(evaluations);
    }

    @GetMapping("/by-staff/{staffId}")
    public ResponseEntity<List<EvaluationDTO>> getEvaluationsByStaffId(@PathVariable UUID staffId) {
        List<EvaluationDTO> evaluations = evaluationService.getAllEvaluationsByStaffId(staffId);
        return ResponseEntity.ok(evaluations);
    }

    @GetMapping("/direct-down-line/{staffId}")
    public ResponseEntity<List<EvaluationDTO>> getEvaluationsByDirectDownLineId(@PathVariable UUID staffId) {
        List<EvaluationDTO> evaluation = evaluationService.getAllDownLineEvaluation(staffId);
        return ResponseEntity.ok(evaluation);
    }
}
