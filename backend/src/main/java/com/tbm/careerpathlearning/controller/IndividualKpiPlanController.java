package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.KpiPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/individual-kpi-plans")
@PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_INDIVIDUAL_KPI')")
public class IndividualKpiPlanController {
    private final KpiPlanService service;
    public IndividualKpiPlanController(KpiPlanService service) {this.service=service;}

    @GetMapping("/periods") @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPeriodContextDto> periods(Authentication auth) {return service.individualPeriods(actor(auth));}
    @GetMapping("/mine") @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPlanDto> mine(Authentication auth) {return service.myIndividualPlans(actor(auth));}
    @GetMapping("/my-assigned") @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPlanDto> assigned(@RequestParam Long reviewPeriodId,Authentication auth) {
        return service.myAssignedPlans(reviewPeriodId,actor(auth));
    }
    @GetMapping("/pending") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiPlanDto> pending(Authentication auth) {return service.pendingIndividualPlans(actor(auth));}
    @GetMapping("/reviews") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiPlanDto> reviews(Authentication auth) {return service.individualReviewPlans(actor(auth));}
    @GetMapping("/{id}")
    public KpiPlanDto get(@PathVariable Long id,Authentication auth) {return service.individualPlan(id,actor(auth));}
    @PostMapping @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<KpiPlanDto> create(@RequestBody KpiPlanRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.createIndividual(request,actor(auth)));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiPlanDto update(@PathVariable Long id,@RequestBody KpiPlanRequest request,Authentication auth) {
        return service.updateIndividual(id,request,actor(auth));
    }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiPlanDto submit(@PathVariable Long id,Authentication auth) {return service.submitIndividual(id,actor(auth));}
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto approve(@PathVariable Long id,Authentication auth) {return service.approveIndividual(id,actor(auth));}
    @PostMapping("/{id}/return") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto returnPlan(@PathVariable Long id,@RequestBody KpiPlanReturnRequest request,Authentication auth) {
        return service.returnIndividual(id,request,actor(auth));
    }
    private UUID actor(Authentication auth) {return UUID.fromString(auth.getName());}
}
