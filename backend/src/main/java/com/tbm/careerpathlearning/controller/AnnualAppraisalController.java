package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.AnnualAppraisalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/annual-appraisals")
@PreAuthorize("hasAnyAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')")
public class AnnualAppraisalController {
    private final AnnualAppraisalService service;
    public AnnualAppraisalController(AnnualAppraisalService service) {this.service=service;}
    @GetMapping("/periods") public List<KpiPeriodContextDto> periods(@RequestParam(defaultValue="false") boolean hrReview,Authentication auth) {
        return service.periods(hrReview,actor(auth));
    }
    @GetMapping("/team") @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public List<AnnualAppraisalCandidateDto> team(@RequestParam Long reviewPeriodId,Authentication auth) {return service.team(reviewPeriodId,actor(auth));}
    @GetMapping("/reviews") @PreAuthorize("hasAuthority('CAN_REVIEW_ANNUAL_APPRAISAL')")
    public List<AnnualAppraisalDto> reviews(@RequestParam Long reviewPeriodId,Authentication auth) {return service.reviews(reviewPeriodId,actor(auth));}
    @GetMapping("/participants/{participantId}/readiness") @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public AnnualAppraisalReadinessDto readiness(@PathVariable Long participantId,Authentication auth) {return service.readiness(participantId,actor(auth));}
    @GetMapping("/participants/{participantId}/history")
    public List<AnnualAppraisalDto> history(@PathVariable Long participantId,Authentication auth) {return service.history(participantId,actor(auth));}
    @GetMapping("/participants/{participantId}/performance-history")
    public List<AnnualPerformanceHistoryDto> performanceHistory(@PathVariable Long participantId,Authentication auth) {
        return service.performanceHistory(participantId,actor(auth));
    }
    @GetMapping("/{id}") public AnnualAppraisalDto get(@PathVariable Long id,Authentication auth) {return service.get(id,actor(auth));}
    @PostMapping @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public ResponseEntity<AnnualAppraisalDto> create(@RequestBody AnnualAppraisalRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.create(request,actor(auth)));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public AnnualAppraisalDto update(@PathVariable Long id,@RequestBody AnnualAppraisalRequest request,Authentication auth) {return service.update(id,request,actor(auth));}
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public AnnualAppraisalDto submit(@PathVariable Long id,@RequestBody AnnualAppraisalActionRequest request,Authentication auth) {return service.submit(id,request,actor(auth));}
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('CAN_REVIEW_ANNUAL_APPRAISAL')")
    public AnnualAppraisalDto approve(@PathVariable Long id,@RequestBody AnnualAppraisalActionRequest request,Authentication auth) {return service.approve(id,request,actor(auth));}
    @PostMapping("/{id}/override-approve") @PreAuthorize("hasAuthority('CAN_REVIEW_ANNUAL_APPRAISAL')")
    public AnnualAppraisalDto override(@PathVariable Long id,@RequestBody AnnualAppraisalActionRequest request,Authentication auth) {return service.overrideAndApprove(id,request,actor(auth));}
    @PostMapping("/{id}/return") @PreAuthorize("hasAuthority('CAN_REVIEW_ANNUAL_APPRAISAL')")
    public AnnualAppraisalDto returnForRevision(@PathVariable Long id,@RequestBody AnnualAppraisalActionRequest request,Authentication auth) {return service.returnForRevision(id,request,actor(auth));}
    private UUID actor(Authentication auth) {return UUID.fromString(auth.getName());}
}
