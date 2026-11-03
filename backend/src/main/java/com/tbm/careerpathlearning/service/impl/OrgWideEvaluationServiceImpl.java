package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.OrgWideCompetencyAverageDto;
import com.tbm.careerpathlearning.dto.OrgWideAverageTrendDto;
import com.tbm.careerpathlearning.dto.OrgWideCompetencyBreakdownDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentRankingDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentTrendDepartmentDto;
import com.tbm.careerpathlearning.dto.OrgWideDepartmentTrendDto;
import com.tbm.careerpathlearning.dto.OrgWideScoreDistributionDto;
import com.tbm.careerpathlearning.dto.OrgWideSummaryDto;
import com.tbm.careerpathlearning.enums.CycleStatus;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import com.tbm.careerpathlearning.repository.OrgWideEvaluationCycleRepository;
import com.tbm.careerpathlearning.repository.OrgWideEvaluationRepository;
import com.tbm.careerpathlearning.repository.OrgWideStaffRepository;
import com.tbm.careerpathlearning.service.OrgWideEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrgWideEvaluationServiceImpl implements OrgWideEvaluationService {

    private static final Long SUPERADMIN_ROLE_ID = 1L;

    @Autowired
    private OrgWideEvaluationRepository orgWideEvaluationRepository;

    @Autowired
    private OrgWideEvaluationCycleRepository orgWideEvaluationCycleRepository;

    @Autowired
    private OrgWideStaffRepository orgWideStaffRepository;

    @Override
    public OrgWideSummaryDto getSummary() {
        OrgWideSummaryDto dto = new OrgWideSummaryDto();
        dto.setTotalStaffEvaluated(orgWideEvaluationRepository.countDistinctEvaluatedStaff());
        dto.setTotalStaff(orgWideStaffRepository.countActiveRealStaff(StaffAccountStatus.ACTIVE, SUPERADMIN_ROLE_ID));
        dto.setOrgAverageScore(0.0);
        dto.setTopDepartment(null);
        dto.setTopDepartmentScore(0.0);
        dto.setPendingAppraisals(0L); // TODO: Replace with PENDING_REVIEW AppraisalRecord count after appraisal table is created.

        Optional<EvaluationCycle> latestClosedCycle = getLatestClosedCycle();
        if (latestClosedCycle.isEmpty()) {
            return dto;
        }

        Long latestCycleId = latestClosedCycle.get().getId();
        dto.setOrgAverageScore(round(orgWideEvaluationRepository.findAverageScoreByCycle(latestCycleId)));

        orgWideEvaluationRepository.findDepartmentAveragesByCycle(latestCycleId)
                .stream()
                .max(Comparator.comparingDouble(department -> nullToZero(department.getAverageScore())))
                .ifPresent(topDepartment -> {
                    dto.setTopDepartment(topDepartment.getDepartmentName());
                    dto.setTopDepartmentScore(round(topDepartment.getAverageScore()));
                });

        return dto;
    }

    @Override
    public List<OrgWideScoreDistributionDto> getScoreDistribution() {
        List<OrgWideScoreDistributionDto> buckets = buildEmptyDistributionBuckets();

        orgWideEvaluationRepository.findLatestScoresByStaff().forEach(scoreProjection -> {
            int bucketIndex = getBucketIndex(scoreProjection.getScore());
            OrgWideScoreDistributionDto bucket = buckets.get(bucketIndex);
            bucket.getStaffNames().add(scoreProjection.getStaffName());
            bucket.setCount((long) bucket.getStaffNames().size());
        });

        return buckets;
    }

    @Override
    public List<OrgWideDepartmentRankingDto> getDepartmentRanking() {
        Optional<EvaluationCycle> latestClosedCycle = getLatestClosedCycle();
        if (latestClosedCycle.isEmpty()) {
            return List.of();
        }

        Long latestCycleId = latestClosedCycle.get().getId();
        Map<String, Double> previousDepartmentScores = orgWideEvaluationCycleRepository
                .findFirstByStatusAndEndDateBeforeOrderByEndDateDesc(CycleStatus.CLOSED, latestClosedCycle.get().getEndDate())
                .map(previousCycle -> orgWideEvaluationRepository.findDepartmentAveragesByCycle(previousCycle.getId())
                        .stream()
                        .collect(Collectors.toMap(
                                OrgWideEvaluationRepository.DepartmentAverageProjection::getDepartmentName,
                                projection -> round(projection.getAverageScore())
                        )))
                .orElseGet(Map::of);

        return orgWideEvaluationRepository.findDepartmentAveragesByCycle(latestCycleId)
                .stream()
                .map(currentDepartment -> {
                    double averageScore = round(currentDepartment.getAverageScore());
                    double previousAverageScore = previousDepartmentScores.getOrDefault(currentDepartment.getDepartmentName(), 0.0);

                    OrgWideDepartmentRankingDto dto = new OrgWideDepartmentRankingDto();
                    dto.setDepartmentName(currentDepartment.getDepartmentName());
                    dto.setAverageScore(averageScore);
                    dto.setPreviousAverageScore(previousAverageScore);
                    dto.setScoreChange(round(averageScore - previousAverageScore));
                    dto.setStaffCount(currentDepartment.getStaffCount());
                    dto.setStatus(getDepartmentStatus(averageScore));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<OrgWideCompetencyBreakdownDto> getCompetencyBreakdown(String departmentName) {
        Optional<EvaluationCycle> latestClosedCycle = getLatestClosedCycle();
        if (latestClosedCycle.isEmpty()) {
            return List.of();
        }

        String normalizedDepartmentName = normalizeDepartmentName(departmentName);
        Map<String, List<OrgWideCompetencyAverageDto>> groupedCompetencies = new LinkedHashMap<>();

        List<OrgWideEvaluationRepository.CompetencyAverageProjection> competencyAverages = normalizedDepartmentName == null
                ? orgWideEvaluationRepository.findCompetencyAveragesByCycle(latestClosedCycle.get().getId())
                : orgWideEvaluationRepository.findCompetencyAveragesByCycleAndDepartment(
                        latestClosedCycle.get().getId(), normalizedDepartmentName);

        competencyAverages.forEach(projection -> {
                    OrgWideCompetencyAverageDto competencyAverageDto = new OrgWideCompetencyAverageDto();
                    competencyAverageDto.setCompetencyName(projection.getCompetencyName());
                    competencyAverageDto.setAverageRating(round(projection.getAverageRating()));

                    groupedCompetencies
                            .computeIfAbsent(projection.getDepartmentName(), key -> new ArrayList<>())
                            .add(competencyAverageDto);
                });

        return groupedCompetencies.entrySet()
                .stream()
                .map(entry -> {
                    OrgWideCompetencyBreakdownDto dto = new OrgWideCompetencyBreakdownDto();
                    dto.setDepartmentName(entry.getKey());
                    dto.setCompetencies(entry.getValue());
                    return dto;
                })
                .toList();
    }

    @Override
    public List<OrgWideDepartmentTrendDto> getDepartmentTrend(Integer years) {
        int yearCount = years == null || years < 1 ? 5 : years;
        List<EvaluationCycle> closedCycles = orgWideEvaluationCycleRepository
                .findClosedCycles(CycleStatus.CLOSED, PageRequest.of(0, yearCount));

        if (closedCycles.isEmpty()) {
            return List.of();
        }

        List<Long> cycleIds = closedCycles.stream()
                .map(EvaluationCycle::getId)
                .toList();

        Map<Integer, List<OrgWideDepartmentTrendDepartmentDto>> groupedTrend = new LinkedHashMap<>();
        orgWideEvaluationRepository.findDepartmentTrendByCycleIds(cycleIds)
                .forEach(projection -> {
                    OrgWideDepartmentTrendDepartmentDto departmentDto = new OrgWideDepartmentTrendDepartmentDto();
                    departmentDto.setDepartmentName(projection.getDepartmentName());
                    departmentDto.setAverageScore(round(projection.getAverageScore()));

                    groupedTrend
                            .computeIfAbsent(projection.getCycleYear(), key -> new ArrayList<>())
                            .add(departmentDto);
                });

        return groupedTrend.entrySet()
                .stream()
                .map(entry -> {
                    OrgWideDepartmentTrendDto dto = new OrgWideDepartmentTrendDto();
                    dto.setYear(entry.getKey());
                    dto.setDepartments(entry.getValue());
                    return dto;
                })
                .toList();
    }

    @Override
    public List<OrgWideAverageTrendDto> getAverageTrend(Integer years) {
        return getDepartmentTrend(years)
                .stream()
                .map(yearTrend -> {
                    OrgWideAverageTrendDto dto = new OrgWideAverageTrendDto();
                    dto.setYear(yearTrend.getYear());
                    double average = yearTrend.getDepartments()
                            .stream()
                            .mapToDouble(department -> nullToZero(department.getAverageScore()))
                            .average()
                            .orElse(0.0);
                    dto.setAverageScore(round(average));
                    return dto;
                })
                .toList();
    }

    private Optional<EvaluationCycle> getLatestClosedCycle() {
        return orgWideEvaluationCycleRepository.findFirstByStatusOrderByEndDateDesc(CycleStatus.CLOSED);
    }

    private List<OrgWideScoreDistributionDto> buildEmptyDistributionBuckets() {
        List<OrgWideScoreDistributionDto> buckets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            OrgWideScoreDistributionDto bucket = new OrgWideScoreDistributionDto();
            bucket.setRange(i == 9 ? "90-100" : (i * 10) + "-" + ((i * 10) + 9));
            bucket.setCount(0L);
            bucket.setStaffNames(new ArrayList<>());
            buckets.add(bucket);
        }
        return buckets;
    }

    private int getBucketIndex(Double score) {
        double safeScore = Math.max(0.0, Math.min(100.0, nullToZero(score)));
        if (safeScore >= 90.0) {
            return 9;
        }
        return (int) Math.floor(safeScore / 10.0);
    }

    private String getDepartmentStatus(Double score) {
        double safeScore = nullToZero(score);
        if (safeScore >= 70.0) {
            return "Excellent";
        }
        if (safeScore >= 50.0) {
            return "Satisfactory";
        }
        return "Needs Improvement";
    }

    private String normalizeDepartmentName(String departmentName) {
        if (departmentName == null || departmentName.trim().isEmpty()) {
            return null;
        }
        return departmentName.trim();
    }

    private double round(Double value) {
        return Math.round(nullToZero(value) * 100.0) / 100.0;
    }

    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
