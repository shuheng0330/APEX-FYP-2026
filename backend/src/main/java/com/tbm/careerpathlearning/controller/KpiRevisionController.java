package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.service.KpiRevisionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api")
@PreAuthorize("hasAuthority('ROLE_USER') or hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
public class KpiRevisionController {
    private final KpiRevisionService service;
    public KpiRevisionController(KpiRevisionService service) {this.service=service;}

    @GetMapping("/kpi-amendment-access/{periodId}") @PreAuthorize("hasAuthority('CAN_MANAGE_KPI_AMENDMENTS')")
    public KpiAmendmentAccessDto access(@PathVariable Long periodId,Authentication auth) {return service.access(periodId,actor(auth));}
    @PutMapping("/kpi-amendment-access/{periodId}") @PreAuthorize("hasAuthority('CAN_MANAGE_KPI_AMENDMENTS')")
    public KpiAmendmentAccessDto access(@PathVariable Long periodId,@RequestBody KpiAmendmentAccessRequest request,Authentication auth) {return service.updateAccess(periodId,request,actor(auth));}

    @GetMapping("/kpi-plan-revisions/periods")
    public List<KpiPeriodContextDto> periods(Authentication auth) {return service.periods(actor(auth));}
    @GetMapping("/kpi-plan-revisions/plans")
    public List<KpiPlanDto> plans(@RequestParam Long reviewPeriodId,Authentication auth) {return service.eligiblePlans(reviewPeriodId,actor(auth));}
    @GetMapping("/kpi-plan-revisions")
    public List<KpiRevisionDto> queue(@RequestParam(required=false) Long reviewPeriodId,@RequestParam(required=false) KpiLevel level,
            @RequestParam(required=false) KpiVersionStatus status,Authentication auth) {return service.queue(reviewPeriodId,level,status,actor(auth));}
    @GetMapping("/kpi-plan-revisions/{id}")
    public KpiRevisionDto get(@PathVariable Long id,Authentication auth) {return service.get(id,actor(auth));}
    @GetMapping("/kpi-plan-revisions/plans/{planId}/versions")
    public List<KpiRevisionDto> history(@PathVariable Long planId,Authentication auth) {return service.history(planId,actor(auth));}

    @PostMapping("/company-kpi-plans/{planId}/revisions") @PreAuthorize("hasAuthority('CAN_REVISE_COMPANY_KPI')")
    public KpiRevisionDto company(@PathVariable Long planId,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.startCompany(planId,request,actor(auth));}
    @PostMapping("/department-kpi-plans/{planId}/revision-requests") @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiRevisionDto department(@PathVariable Long planId,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.requestDepartment(planId,request,actor(auth));}
    @PostMapping("/individual-kpi-plans/{planId}/revision-requests") @PreAuthorize("hasAuthority('CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS')")
    public KpiRevisionDto individual(@PathVariable Long planId,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.requestIndividual(planId,request,actor(auth));}
    @PutMapping("/kpi-plan-revisions/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto update(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.update(id,request,actor(auth));}
    @PostMapping("/kpi-plan-revisions/{id}/prepare")
    @PreAuthorize("hasAnyAuthority('CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto prepare(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.prepare(id,request,actor(auth));}
    @PostMapping("/kpi-plan-revisions/{id}/submit")
    @PreAuthorize("hasAnyAuthority('CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS')")
    public KpiRevisionDto submit(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.submit(id,request,actor(auth));}
    @PostMapping("/kpi-plan-revisions/{id}/approve")
    @PreAuthorize("hasAnyAuthority('CAN_APPROVE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto approve(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.approve(id,request,actor(auth));}
    @PostMapping("/kpi-plan-revisions/{id}/return")
    @PreAuthorize("hasAnyAuthority('CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto returnToHr(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.returnToHr(id,request,actor(auth));}
    @PostMapping("/kpi-plan-revisions/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('CAN_REVISE_COMPANY_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto confirm(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.confirm(id,request,actor(auth));}
    @PostMapping("/kpi-plan-revisions/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS')")
    public KpiRevisionDto cancel(@PathVariable Long id,@RequestBody KpiRevisionRequest request,Authentication auth) {return service.cancel(id,request,actor(auth));}
    private UUID actor(Authentication auth) {return UUID.fromString(auth.getName());}
}
