package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TeamPerformanceDto;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.service.TeamPerformanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/annual-performance/team")
@PreAuthorize("hasAnyAuthority('CAN_VIEW_TEAM_PERFORMANCE','CAN_VIEW_DEPARTMENT_PERFORMANCE','CAN_VIEW_ORGANISATION_PERFORMANCE')")
public class TeamPerformanceController {
    private final TeamPerformanceService service;
    public TeamPerformanceController(TeamPerformanceService service) { this.service = service; }

    @GetMapping("/scopes")
    public List<ScopeOption> scopes(Authentication auth) { return service.scopes(actor(auth)); }

    @GetMapping("/periods")
    public List<Period> periods(@RequestParam Scope scope, @RequestParam(required = false) Long departmentId, Authentication auth) {
        return service.periods(scope, departmentId, actor(auth));
    }

    @GetMapping
    public TeamPerformanceDto dashboard(@RequestParam Scope scope, @RequestParam(required = false) Long departmentId,
            @RequestParam Long reviewPeriodId, @RequestParam(defaultValue = "5") int historyYears,
            @RequestParam(defaultValue = "FINAL") Metric metric, Authentication auth) {
        return service.dashboard(scope, departmentId, reviewPeriodId, historyYears, metric, actor(auth));
    }

    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
