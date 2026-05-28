package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrgWideCompetencyBreakdownDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentRankingDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentTrendDto;
import com.tbm.careerpathlearning.dto.OrgWideScoreDistributionDto;
import com.tbm.careerpathlearning.dto.OrgWideSummaryDto;

import java.util.List;

public interface OrgWideEvaluationService {

    OrgWideSummaryDto getSummary();

    List<OrgWideScoreDistributionDto> getScoreDistribution();

    List<OrgWideDepartmentRankingDto> getDepartmentRanking();

    List<OrgWideCompetencyBreakdownDto> getCompetencyBreakdown(String departmentName);

    List<OrgWideDepartmentTrendDto> getDepartmentTrend(Integer years);
}
