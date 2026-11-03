package com.tbm.careerpathlearning.controller;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.KpiPlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestController @RequestMapping("/api/company-kpi-plans") @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
public class CompanyKpiPlanController {
    private final KpiPlanService service;
    public CompanyKpiPlanController(KpiPlanService service) {this.service=service;}
    @GetMapping public List<KpiPlanDto> list() {return service.companyPlans();}
    @GetMapping("/periods") public List<KpiPeriodContextDto> periods() {return service.companyPeriods();}
    @GetMapping("/{id}") public KpiPlanDto get(@PathVariable Long id) {return service.companyPlan(id);}
    @PostMapping("/{id}/publish") public KpiPlanDto publish(@PathVariable Long id,Authentication auth) {
        return service.publishCompany(id,UUID.fromString(auth.getName()));
    }
    @PostMapping public ResponseEntity<KpiPlanDto> create(@RequestBody KpiPlanRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.createCompany(request,UUID.fromString(auth.getName())));
    }
    @PutMapping("/{id}") public KpiPlanDto update(@PathVariable Long id,@RequestBody KpiPlanRequest request,Authentication auth) {
        return service.updateCompany(id,request,UUID.fromString(auth.getName()));
    }
}
