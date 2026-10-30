package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.OrgWideCompetencyBreakdownDto;
import com.tbm.careerpathlearning.dto.OrgWideAverageTrendDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentRankingDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentTrendDto;
import com.tbm.careerpathlearning.dto.OrgWideScoreDistributionDto;
import com.tbm.careerpathlearning.dto.OrgWideSummaryDto;
import com.tbm.careerpathlearning.service.OrgWideEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation/org")
@CrossOrigin
public class OrgWideEvaluationController {

    @Autowired
    private OrgWideEvaluationService orgWideEvaluationService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/summary")
    public ResponseEntity<OrgWideSummaryDto> getSummary() {
        return ResponseEntity.ok(orgWideEvaluationService.getSummary());
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/score-distribution")
    public ResponseEntity<List<OrgWideScoreDistributionDto>> getScoreDistribution() {
        return ResponseEntity.ok(orgWideEvaluationService.getScoreDistribution());
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/department-ranking")
    public ResponseEntity<List<OrgWideDepartmentRankingDto>> getDepartmentRanking() {
        return ResponseEntity.ok(orgWideEvaluationService.getDepartmentRanking());
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/competency-breakdown")
    public ResponseEntity<List<OrgWideCompetencyBreakdownDto>> getCompetencyBreakdown(
            @RequestParam(required = false) String departmentName) {
        return ResponseEntity.ok(orgWideEvaluationService.getCompetencyBreakdown(departmentName));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/competency-breakdown/all")
    public ResponseEntity<List<OrgWideCompetencyBreakdownDto>> getAllCompetencyBreakdown() {
        return ResponseEntity.ok(orgWideEvaluationService.getCompetencyBreakdown(null));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/department-trend")
    public ResponseEntity<List<OrgWideDepartmentTrendDto>> getDepartmentTrend(
            @RequestParam(defaultValue = "5") Integer years) {
        return ResponseEntity.ok(orgWideEvaluationService.getDepartmentTrend(years));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName()
            )
            """)
    @GetMapping("/average-trend")
    public ResponseEntity<List<OrgWideAverageTrendDto>> getAverageTrend(
            @RequestParam(defaultValue = "5") Integer years) {
        return ResponseEntity.ok(orgWideEvaluationService.getAverageTrend(years));
    }
}
