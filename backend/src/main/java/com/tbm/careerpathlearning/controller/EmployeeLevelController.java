package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.EmployeeLevelDto;
import com.tbm.careerpathlearning.service.EmployeeLevelService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/employee-levels")
public class EmployeeLevelController {
    private final EmployeeLevelService service;
    public EmployeeLevelController(EmployeeLevelService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_ROLE', 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD')")
    public List<EmployeeLevelDto> options() { return service.options(); }
}
