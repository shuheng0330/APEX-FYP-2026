package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.KpiPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/individual-kpi-assistance")
@PreAuthorize("hasAnyAuthority('CAN_REVIEW_INDIVIDUAL_KPI','CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE')")
public class IndividualKpiAssistanceController {
    private final KpiPlanService service;
    public IndividualKpiAssistanceController(KpiPlanService service) {this.service=service;}
    @GetMapping("/employees") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiAssistanceEmployeeDto> employees(Authentication auth) {return service.assistanceEmployees(actor(auth));}
    @GetMapping
    public List<KpiAssistanceDto> list(Authentication auth) {return service.assistanceCases(actor(auth));}
    @GetMapping("/{id}")
    public KpiAssistanceDto get(@PathVariable Long id,Authentication auth) {return service.assistanceCase(id,actor(auth));}
    @PostMapping @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public ResponseEntity<KpiAssistanceDto> request(@Valid @RequestBody KpiAssistanceRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.requestAssistance(request,actor(auth)));
    }
    @PostMapping("/{id}/authorize") @PreAuthorize("hasAuthority('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE')")
    public KpiAssistanceDto authorize(@PathVariable Long id,Authentication auth) {return service.authorizeAssistance(id,actor(auth));}
    @GetMapping("/{id}/plan") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto plan(@PathVariable Long id,Authentication auth) {return service.assistedIndividualPlan(id,actor(auth));}
    @PostMapping("/{id}/plan") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public ResponseEntity<KpiPlanDto> create(@PathVariable Long id,@RequestBody AssistedIndividualKpiPlanRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.createAssistedIndividual(id,request,actor(auth)));
    }
    @PutMapping("/{id}/plan") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto update(@PathVariable Long id,@RequestBody AssistedIndividualKpiPlanRequest request,Authentication auth) {
        return service.updateAssistedIndividual(id,request,actor(auth));
    }
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto confirm(@PathVariable Long id,Authentication auth) {return service.confirmAssistedIndividual(id,actor(auth));}
    private UUID actor(Authentication auth) {return UUID.fromString(auth.getName());}
}
