package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/annual-performance/organisation")
@PreAuthorize("hasAuthority('CAN_VIEW_ORGANISATION_PERFORMANCE')")
public class OrganisationPerformanceController {
    private final OrganisationPerformanceService service;
    private final OrganisationPerformanceExportService export;
    public OrganisationPerformanceController(OrganisationPerformanceService service,OrganisationPerformanceExportService export) {
        this.service=service;this.export=export;
    }
    @GetMapping("/periods")
    public List<Period> periods(Authentication auth) { return service.periods(actor(auth)); }
    @GetMapping
    public OrganisationPerformanceDto dashboard(@RequestParam Long reviewPeriodId,@RequestParam(defaultValue="5") int historyYears,
            @RequestParam(defaultValue="FINAL") Metric metric,Authentication auth) {
        return service.dashboard(reviewPeriodId,historyYears,metric,actor(auth));
    }
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Long reviewPeriodId,@RequestParam(defaultValue="5") int historyYears,
            @RequestParam(defaultValue="FINAL") Metric metric,Authentication auth) throws IOException {
        var dashboard=service.dashboard(reviewPeriodId,historyYears,metric,actor(auth));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("organisation-performance-period-"+reviewPeriodId+".xlsx").build().toString())
                .body(export.write(dashboard));
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
