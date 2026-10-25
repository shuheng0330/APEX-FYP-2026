package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.KpiScoreCalculationDto;
import com.tbm.careerpathlearning.dto.KpiScoreBreakdownDto;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class KpiCheckpointAggregateService {
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    private final KpiCheckpointResultRepository aggregates;
    private final KpiCentralResultRepository centralResults;
    private final KpiAssessmentRepository assessments;
    private final EmployeeKpiAssignmentRepository assignments;
    private final Clock clock;
    private final KpiPlanMapper kpis;
    private final CompanyKpiCheckpointResolver companyCheckpoints;

    public KpiCheckpointAggregateService(KpiCheckpointResultRepository aggregates,
            KpiCentralResultRepository centralResults,KpiAssessmentRepository assessments,
            EmployeeKpiAssignmentRepository assignments,@Qualifier("annualKpiReviewClock") Clock clock,
            KpiPlanMapper kpis,CompanyKpiCheckpointResolver companyCheckpoints) {
        this.aggregates=aggregates;this.centralResults=centralResults;this.assessments=assessments;
        this.assignments=assignments;this.clock=clock;
        this.kpis=kpis;
        this.companyCheckpoints=companyCheckpoints;
    }

    public KpiScoreBreakdownDto breakdown(KpiAssessment assessment) {
        var weights=weights(assessment);
        var versions=versions(assessment,false);
        var levels=new ArrayList<KpiScoreBreakdownDto.Level>();
        var companyCheckpoint=companyCheckpoints.resolve(assessment);
        for(var level:KpiLevel.values()) {
            var allocation=weights.get(level);
            var version=versions.get(level);
            Integer versionNumber=version==null?null:version.getVersionNumber();
            if(allocation.signum()==0) {
                levels.add(new KpiScoreBreakdownDto.Level(level,allocation,versionNumber,null,null,
                        KpiScoreBreakdownDto.Availability.NOT_APPLICABLE,BigDecimal.ZERO,List.of()));
                continue;
            }
            var publication=level==KpiLevel.INDIVIDUAL?Optional.<KpiCentralResult>empty():
                    published(version,(level==KpiLevel.COMPANY?companyCheckpoint:assessment.getCheckpoint()).getId());
            boolean available=level==KpiLevel.INDIVIDUAL?assessment.getStatus()==KpiAssessmentStatus.REVIEWED:
                    publication.isPresent();
            List<KpiScoreBreakdownDto.Item> items=List.of();
            BigDecimal contribution=null;
            if(available && level==KpiLevel.INDIVIDUAL) {
                contribution=individualContribution(assessment,allocation);
                items=assessment.getItems().stream().map(item->new KpiScoreBreakdownDto.Item(
                        kpis.toDto(item.getAssignment().getKpi()),null,item.getSuperiorPoint())).toList();
            } else if(available) {
                var result=publication.orElseThrow();
                contribution=centralContribution(result,allocation);
                items=result.getItems().stream().map(item->new KpiScoreBreakdownDto.Item(
                        kpis.toDto(item.getKpi()),item.getActualAchievement(),item.getPoint())).toList();
            }
            levels.add(new KpiScoreBreakdownDto.Level(level,allocation,versionNumber,
                    publication.map(KpiCentralResult::getEditionNumber).orElse(null),
                    level==KpiLevel.INDIVIDUAL && available?assessment.getReviewedAt():
                            publication.map(KpiCentralResult::getPublishedAt).orElse(null),
                    available?KpiScoreBreakdownDto.Availability.AVAILABLE:KpiScoreBreakdownDto.Availability.PENDING,
                    contribution,items,level==KpiLevel.COMPANY?new KpiScoreBreakdownDto.ReportingCheckpoint(
                            companyCheckpoint.getId(),companyCheckpoint.getReviewFrequency(),companyCheckpoint.getStartDate(),companyCheckpoint.getEndDate()):null));
        }
        return new KpiScoreBreakdownDto(assessment.getId(),assessment.getCheckpointScore(),levels);
    }

    public List<KpiLevel> pendingLevels(KpiAssessment assessment) {
        if(assessment.getAssessmentModel()!=KpiAssessmentModel.CENTRAL_RESULTS) return List.of();
        var weights=weights(assessment);
        var versions=versions(assessment,false);
        var pending=new ArrayList<KpiLevel>();
        for(var level:KpiLevel.values()) {
            if(weights.get(level).signum()==0) continue;
            if(level==KpiLevel.INDIVIDUAL) {
                if(assessment.getStatus()!=KpiAssessmentStatus.REVIEWED) pending.add(level);
            } else if(published(versions.get(level),(level==KpiLevel.COMPANY
                    ?companyCheckpoints.resolve(assessment):assessment.getCheckpoint()).getId()).isEmpty()) pending.add(level);
        }
        return pending;
    }

    public void refresh(KpiAssessment assessment,UUID actor,String reason) {
        if(assessment.getAssessmentModel()!=KpiAssessmentModel.CENTRAL_RESULTS
                || assessment.getStatus()!=KpiAssessmentStatus.REVIEWED) return;
        var weights=weights(assessment);
        var versions=versions(assessment,true);
        var companyCheckpoint=companyCheckpoints.resolve(assessment);
        var company=published(versions.get(KpiLevel.COMPANY),companyCheckpoint.getId());
        var department=published(versions.get(KpiLevel.DEPARTMENT),assessment.getCheckpoint().getId());
        var aggregate=aggregates.lockByScope(assessment.getParticipant().getId(),assessment.getCheckpoint().getId())
                .orElseGet(()->{
                    var row=new KpiCheckpointResult();row.setParticipantId(assessment.getParticipant().getId());
                    row.setReviewPeriodId(assessment.getReviewPeriodId());row.setCheckpointId(assessment.getCheckpoint().getId());
                    row.setCompanyCheckpointId(companyCheckpoint.getId());
                    row.setReviewFrequency(assessment.getReviewFrequency());return row;
                });
        putVersion(aggregate,KpiLevel.COMPANY,versions.get(KpiLevel.COMPANY));
        putVersion(aggregate,KpiLevel.DEPARTMENT,versions.get(KpiLevel.DEPARTMENT));
        putVersion(aggregate,KpiLevel.INDIVIDUAL,versions.get(KpiLevel.INDIVIDUAL));
        var companyId=weights.get(KpiLevel.COMPANY).signum()>0?company.map(KpiCentralResult::getId).orElse(null):null;
        var departmentId=weights.get(KpiLevel.DEPARTMENT).signum()>0?department.map(KpiCentralResult::getId).orElse(null):null;
        BigDecimal score=null;
        if((weights.get(KpiLevel.COMPANY).signum()==0 || company.isPresent())
                && (weights.get(KpiLevel.DEPARTMENT).signum()==0 || department.isPresent())) {
            score=individualContribution(assessment,weights.get(KpiLevel.INDIVIDUAL));
            if(companyId!=null) score=score.add(centralContribution(company.orElseThrow(),weights.get(KpiLevel.COMPANY)));
            if(departmentId!=null) score=score.add(centralContribution(department.orElseThrow(),weights.get(KpiLevel.DEPARTMENT)));
            score=score.setScale(4,RoundingMode.HALF_UP);
        }
        if(score!=null && (aggregate.getScore()==null || score.compareTo(aggregate.getScore())!=0
                || !Objects.equals(companyId,aggregate.getCompanyResultId())
                || !Objects.equals(departmentId,aggregate.getDepartmentResultId()))) {
            var history=new ArrayList<>(aggregate.getCalculationHistory());
            history.add(new KpiScoreCalculationDto(aggregate.getScore(),score,OffsetDateTime.now(clock),
                    companyId,departmentId,actor,reason));aggregate.setCalculationHistory(history);
            aggregate.setCalculatedAt(OffsetDateTime.now(clock));
        }
        if(score==null) aggregate.setCalculatedAt(null);
        aggregate.setCompanyResultId(companyId);aggregate.setDepartmentResultId(departmentId);
        aggregate.setScore(score);aggregates.saveAndFlush(aggregate);
        assessment.setCheckpointScore(score);assessments.saveAndFlush(assessment);
    }

    public void refreshForPublication(KpiCentralResult result,UUID actor) {
        var period=result.getCheckpoint().getReviewPeriod();
        var affected=result.getLevel()==KpiLevel.COMPANY && period!=null
                && period.getFrequencyPolicy()==KpiFrequencyPolicy.DEPARTMENT_BASED
                ?assessments.findAllByReviewPeriodIdAndCheckpointEndDateAndStatus(
                        result.getReviewPeriodId(),result.getCheckpoint().getEndDate(),KpiAssessmentStatus.REVIEWED)
                :assessments.findAllByReviewPeriodIdAndCheckpointIdAndStatus(
                        result.getReviewPeriodId(),result.getCheckpoint().getId(),KpiAssessmentStatus.REVIEWED);
        for(var assessment:affected) {
            if(result.getLevel()==KpiLevel.COMPANY
                    && !Objects.equals(companyCheckpoints.resolve(assessment).getId(),result.getCheckpoint().getId())) continue;
            var version=versions(assessment,false).get(result.getLevel());
            if(version!=null && Objects.equals(version.getId(),result.getPlanVersion().getId()))
                refresh(assessment,actor,"Central "+result.getLevel()+" KPI results published");
        }
    }

    private Map<KpiLevel,KpiPlanVersion> versions(KpiAssessment assessment,boolean bindMissing) {
        var found=new EnumMap<KpiLevel,KpiPlanVersion>(KpiLevel.class);
        assessment.getPlanVersions().forEach(b->found.put(b.getLevel(),b.getVersion()));
        for(var assignment:assignments.findAllByParticipantId(assessment.getParticipant().getId())) {
            if(!Objects.equals(assignment.getReviewPeriodId(),assessment.getReviewPeriodId())) continue;
            var kpi=assignment.getKpi();var plan=kpi.getPlan();var version=kpi.getVersion();
            if(version==null || plan.getCurrentVersion()==null || !Objects.equals(version.getId(),plan.getCurrentVersion().getId())) continue;
            if(plan.getLevel()==KpiLevel.COMPANY && plan.getStatus()!=KpiPlanStatus.PUBLISHED
                    || plan.getLevel()!=KpiLevel.COMPANY && plan.getStatus()!=KpiPlanStatus.APPROVED) continue;
            if(!found.containsKey(plan.getLevel())) {
                found.put(plan.getLevel(),version);
                if(bindMissing) {
                    var binding=new KpiAssessmentPlanVersion();binding.setAssessment(assessment);
                    binding.setReviewPeriodId(assessment.getReviewPeriodId());binding.setLevel(plan.getLevel());
                    binding.setVersion(version);assessment.getPlanVersions().add(binding);
                }
            }
        }
        return found;
    }

    private Optional<KpiCentralResult> published(KpiPlanVersion version,Long checkpointId) {
        return version==null?Optional.empty():centralResults.findByPlanVersionIdAndCheckpointIdAndStatus(
                version.getId(),checkpointId,KpiCentralResultStatus.PUBLISHED);
    }

    private void putVersion(KpiCheckpointResult row,KpiLevel level,KpiPlanVersion version) {
        if(version==null) return;
        Long existing=switch(level) {
            case COMPANY->row.getCompanyVersionId();case DEPARTMENT->row.getDepartmentVersionId();
            case INDIVIDUAL->row.getIndividualVersionId();
        };
        if(existing!=null && !existing.equals(version.getId()))
            throw new BadRequestException("A started checkpoint cannot switch KPI versions");
        switch(level) {
            case COMPANY->row.setCompanyVersionId(version.getId());
            case DEPARTMENT->row.setDepartmentVersionId(version.getId());
            case INDIVIDUAL->row.setIndividualVersionId(version.getId());
        }
    }

    private Map<KpiLevel,BigDecimal> weights(KpiAssessment assessment) {
        var allocation=assessment.getParticipant().getEmployeeLevelConfiguration();
        if(allocation==null) throw new BadRequestException("The recorded KPI allocation is missing");
        var weights=new EnumMap<KpiLevel,BigDecimal>(KpiLevel.class);
        weights.put(KpiLevel.COMPANY,allocation.getCompanyKpiWeight());
        weights.put(KpiLevel.DEPARTMENT,allocation.getDepartmentKpiWeight());
        weights.put(KpiLevel.INDIVIDUAL,allocation.getIndividualKpiWeight());
        BigDecimal total=BigDecimal.ZERO;
        for(var weight:weights.values()) {
            validWeight(weight);total=total.add(weight);
        }
        if(total.compareTo(HUNDRED)!=0) throw new BadRequestException("The recorded KPI allocation must total 100%");
        return weights;
    }

    private BigDecimal individualContribution(KpiAssessment assessment,BigDecimal allocation) {
        if(allocation.signum()==0) return BigDecimal.ZERO;
        BigDecimal total=BigDecimal.ZERO,score=BigDecimal.ZERO;
        for(var item:assessment.getItems()) {
            if(item.getAssignment().getKpi().getPlan().getLevel()!=KpiLevel.INDIVIDUAL)
                throw new BadRequestException("Central-model reviews contain Individual KPIs only");
            if(item.getSuperiorPoint()==null || item.getSuperiorPoint()<1 || item.getSuperiorPoint()>5)
                throw new BadRequestException("Complete every Superior Individual KPI point");
            var weight=item.getAssignment().getKpi().getWeightage();validWeight(weight);total=total.add(weight);
            score=score.add(BigDecimal.valueOf(item.getSuperiorPoint()).multiply(weight).multiply(allocation)
                    .divide(new BigDecimal("500")));
        }
        if(total.compareTo(HUNDRED)!=0) throw new BadRequestException("Individual KPI weightages must total 100%");
        return score;
    }

    private BigDecimal centralContribution(KpiCentralResult result,BigDecimal allocation) {
        BigDecimal total=BigDecimal.ZERO,score=BigDecimal.ZERO;
        for(var item:result.getItems()) {
            if(item.getPoint()==null || item.getPoint()<1 || item.getPoint()>5)
                throw new BadRequestException("Published central KPI points are incomplete");
            var weight=item.getKpi().getWeightage();validWeight(weight);total=total.add(weight);
            score=score.add(BigDecimal.valueOf(item.getPoint()).multiply(weight).multiply(allocation)
                    .divide(new BigDecimal("500")));
        }
        if(total.compareTo(HUNDRED)!=0) throw new BadRequestException("Published central KPI weightages must total 100%");
        return score;
    }

    private void validWeight(BigDecimal weight) {
        if(weight==null || weight.signum()<0 || weight.compareTo(HUNDRED)>0 || weight.stripTrailingZeros().scale()>2)
            throw new BadRequestException("The recorded KPI weights must be 0 to 100 with at most two decimal places");
    }
}
