package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.ReviewPeriodClosureDto;
import com.tbm.careerpathlearning.exception.ReviewPeriodClosureBlockedException;
import com.tbm.careerpathlearning.service.AnnualReviewPeriodClosureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/annual-kpi-review-periods")
@PreAuthorize("hasAuthority('CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD')")
public class AnnualReviewPeriodClosureController {
    private final AnnualReviewPeriodClosureService service;
    public AnnualReviewPeriodClosureController(AnnualReviewPeriodClosureService service) { this.service=service; }
    @GetMapping("/{id}/closure-readiness")
    public ReviewPeriodClosureDto readiness(@PathVariable Long id,Authentication auth) {
        return service.readiness(id,UUID.fromString(auth.getName()));
    }
    @PostMapping("/{id}/close")
    public ReviewPeriodClosureDto close(@PathVariable Long id,Authentication auth) {
        return service.close(id,UUID.fromString(auth.getName()));
    }
    @ExceptionHandler(ReviewPeriodClosureBlockedException.class)
    public ResponseEntity<ReviewPeriodClosureDto> blocked(ReviewPeriodClosureBlockedException failure) {
        return ResponseEntity.status(409).body(failure.readiness());
    }
}
