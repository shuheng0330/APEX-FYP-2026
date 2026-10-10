package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TeamReviewPeriodsDto;
import com.tbm.careerpathlearning.service.TeamReviewPeriodService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/team-reviews")
@PreAuthorize("hasAnyAuthority('CAN_REVIEW_INDIVIDUAL_KPI','CAN_REVIEW_KPI_ASSESSMENT','CAN_REVIEW_ATTITUDE_EVALUATION')")
public class TeamReviewController {
    private final TeamReviewPeriodService service;
    public TeamReviewController(TeamReviewPeriodService service) { this.service=service; }
    @GetMapping("/periods")
    public TeamReviewPeriodsDto periods(Authentication auth) { return service.periods(UUID.fromString(auth.getName())); }
}
