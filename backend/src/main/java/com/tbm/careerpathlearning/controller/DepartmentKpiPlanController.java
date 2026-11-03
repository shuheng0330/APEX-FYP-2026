package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.KpiPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/department-kpi-plans")
@PreAuthorize("hasAnyAuthority('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')")
public class DepartmentKpiPlanController {
    private final KpiPlanService service;
    public DepartmentKpiPlanController(KpiPlanService service) {this.service=service;}
    @GetMapping public List<KpiPlanDto> list(Authentication auth) {return service.departmentPlans(actor(auth));}
    @GetMapping("/periods") public List<KpiPeriodContextDto> periods(Authentication auth) {return service.departmentPeriods(actor(auth));}
    @GetMapping("/departments") public List<KpiDepartmentOptionDto> departments(Authentication auth) {return service.departmentOptions(actor(auth));}
    @GetMapping("/pending") @PreAuthorize("hasAuthority('CAN_APPROVE_DEPARTMENT_KPI')")
    public List<KpiPlanDto> pending(Authentication auth) {return service.pendingDepartmentPlans(actor(auth));}
    @GetMapping("/{id}") public KpiPlanDto get(@PathVariable Long id,Authentication auth) {return service.departmentPlan(id,actor(auth));}
    @PostMapping @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public ResponseEntity<KpiPlanDto> create(@RequestBody KpiPlanRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.createDepartment(request,actor(auth)));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiPlanDto update(@PathVariable Long id,@RequestBody KpiPlanRequest request,Authentication auth) {
        return service.updateDepartment(id,request,actor(auth));
    }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiPlanDto submit(@PathVariable Long id,Authentication auth) {return service.submitDepartment(id,actor(auth));}
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('CAN_APPROVE_DEPARTMENT_KPI')")
    public KpiPlanDto approve(@PathVariable Long id,Authentication auth) {return service.approveDepartment(id,actor(auth));}
    @PostMapping("/{id}/return") @PreAuthorize("hasAuthority('CAN_APPROVE_DEPARTMENT_KPI')")
    public KpiPlanDto returnPlan(@PathVariable Long id,@RequestBody KpiPlanReturnRequest request,Authentication auth) {
        return service.returnDepartment(id,request,actor(auth));
    }
    private UUID actor(Authentication auth) {return UUID.fromString(auth.getName());}
}
