package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.StaffPerformanceDto;
import com.tbm.careerpathlearning.service.StaffPerformanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/annual-performance")
@PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_VIEW_TEAM_PERFORMANCE','CAN_VIEW_DEPARTMENT_PERFORMANCE','CAN_VIEW_ORGANISATION_PERFORMANCE')")
public class StaffPerformanceController {
    private final StaffPerformanceService service;

    public StaffPerformanceController(StaffPerformanceService service) { this.service = service; }

    @GetMapping("/staff/{employeeId}/periods")
    public List<StaffPerformanceDto.Period> periods(@PathVariable UUID employeeId, Authentication auth) {
        return service.periods(employeeId, actor(auth));
    }

    @GetMapping("/staff/{employeeId}")
    public StaffPerformanceDto dashboard(@PathVariable UUID employeeId, @RequestParam Long reviewPeriodId,
            @RequestParam(defaultValue = "5") int historyYears, Authentication auth) {
        return service.dashboard(employeeId, reviewPeriodId, historyYears, actor(auth));
    }

    @GetMapping("/participants/{participantId}/checkpoints/{checkpointId}")
    public StaffPerformanceDto.CheckpointDetails checkpoint(@PathVariable Long participantId,
            @PathVariable Long checkpointId, Authentication auth) {
        return service.checkpoint(participantId, checkpointId, actor(auth));
    }

    @GetMapping("/participants/{participantId}/attitude")
    public StaffPerformanceDto.Attitude attitude(@PathVariable Long participantId, Authentication auth) {
        return service.attitude(participantId, actor(auth));
    }

    @GetMapping("/participants/{participantId}/appraisals/{appraisalId}")
    public StaffPerformanceDto.ApprovedAppraisal appraisal(@PathVariable Long participantId,
            @PathVariable Long appraisalId, Authentication auth) {
        return service.appraisal(participantId, appraisalId, actor(auth));
    }

    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
