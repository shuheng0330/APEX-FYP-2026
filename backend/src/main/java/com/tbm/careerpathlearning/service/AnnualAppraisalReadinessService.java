package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

/** Reads official assessment results; it never creates assessments or recalculates their stored scores. */
@Service @Transactional(readOnly=true)
public class AnnualAppraisalReadinessService {
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    private final ReviewCheckpointRepository checkpoints;
    private final KpiAssessmentRepository assessments;
    private final AttitudeAssessmentRepository attitudes;
    private final KpiCheckpointAggregateService aggregates;
    private final KpiPlanMapper plans;

    public AnnualAppraisalReadinessService(ReviewCheckpointRepository checkpoints,KpiAssessmentRepository assessments,
            AttitudeAssessmentRepository attitudes,KpiCheckpointAggregateService aggregates,KpiPlanMapper plans) {
        this.checkpoints=checkpoints;this.assessments=assessments;this.attitudes=attitudes;
        this.aggregates=aggregates;this.plans=plans;
    }

    /** An unavailable historical source must not prevent a different result from being saved. */
    public boolean readyForNotification(ReviewPeriodParticipant participant) {
        try {
            return evaluate(participant).ready();
        } catch (com.tbm.careerpathlearning.exception.BadRequestException unavailable) {
            return false;
        }
    }

    public AnnualAppraisalReadinessDto evaluate(ReviewPeriodParticipant participant) {
        var period=participant.getReviewPeriod();
        List<String> blockers=new ArrayList<>();
        var required=checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(period.getId(),participant.getReviewFrequency())
                .stream().sorted(Comparator.comparing(ReviewCheckpoint::getEndDate).thenComparing(ReviewCheckpoint::getSequenceNumber)
                        .thenComparing(ReviewCheckpoint::getId)).toList();
        var existing=new HashMap<Long,KpiAssessment>();
        assessments.findAllByParticipantId(participant.getId()).forEach(a->existing.put(a.getCheckpoint().getId(),a));
        List<AnnualAppraisalSnapshotDto.Checkpoint> sources=new ArrayList<>();
        if(required.isEmpty()) blockers.add("No KPI Review Checkpoints are configured for this employee");
        for(var checkpoint:required) {
            var assessment=existing.get(checkpoint.getId());
            String label=checkpoint.getStartDate()+" to "+checkpoint.getEndDate();
            boolean reviewed=assessment!=null && assessment.getStatus()==KpiAssessmentStatus.REVIEWED;
            if(!reviewed) blockers.add("Complete Superior KPI review for "+label);
            BigDecimal score=reviewed?assessment.getCheckpointScore():null;
            KpiScoreBreakdownDto breakdown=null;
            if(reviewed && assessment.getAssessmentModel()==KpiAssessmentModel.CENTRAL_RESULTS) {
                var pending=aggregates.pendingLevels(assessment);
                if(!pending.isEmpty()) {
                    blockers.add("Waiting for "+pending+" KPI results for "+label);score=null;
                }
                breakdown=aggregates.breakdown(assessment);
            } else if(reviewed) breakdown=legacyBreakdown(assessment);
            if(reviewed && !validScore(score)) blockers.add("Checkpoint KPI Score is unavailable for "+label);
            var bindings=assessment==null?List.<AnnualAppraisalSnapshotDto.Version>of():assessment.getPlanVersions().stream()
                    .sorted(Comparator.comparing(KpiAssessmentPlanVersion::getLevel))
                    .map(b->new AnnualAppraisalSnapshotDto.Version(b.getLevel(),b.getVersion().getId(),b.getVersion().getVersionNumber())).toList();
            sources.add(new AnnualAppraisalSnapshotDto.Checkpoint(checkpoint.getId(),checkpoint.getSequenceNumber(),
                    checkpoint.getStartDate(),checkpoint.getEndDate(),assessment==null?null:assessment.getId(),
                    assessment==null?null:assessment.getStatus(),score,assessment==null?null:assessment.getReviewedAt(),bindings,breakdown));
        }
        var attitude=attitudes.findByParticipantId(participant.getId()).orElse(null);
        boolean attitudeReviewed=attitude!=null && attitude.getStatus()==AttitudeAssessmentStatus.REVIEWED;
        BigDecimal attitudeScore=attitudeReviewed?attitude.getAttitudeScore():null;
        if(!attitudeReviewed || !validScore(attitudeScore)) blockers.add("Complete Superior Attitude Evaluation");
        BigDecimal kpiWeight=period.getKpiPerformanceWeight(),attitudeWeight=period.getAttitudeEvaluationWeight();
        boolean weightsValid=AnnualPerformanceCalculation.validWeights(kpiWeight,attitudeWeight);
        if(!weightsValid) blockers.add("The KPI and Attitude appraisal weights must total 100%");
        if(period.getAnnualKpiConsolidationMethod()==null) blockers.add("The annual KPI consolidation method is not configured");
        BigDecimal annualKpi=AnnualPerformanceCalculation.annualKpi(sources.stream()
                .map(AnnualAppraisalSnapshotDto.Checkpoint::score).toList(),period.getAnnualKpiConsolidationMethod());
        BigDecimal finalScore=AnnualPerformanceCalculation.finalScore(annualKpi,attitudeScore,kpiWeight,attitudeWeight);
        AnnualAppraisalSnapshotDto.Attitude attitudeSource=null;
        if(attitude!=null) attitudeSource=new AnnualAppraisalSnapshotDto.Attitude(attitude.getId(),attitude.getConfiguration().getId(),
                attitude.getEvaluationFormat(),attitude.getStatus(),attitude.getReviewedAt(),attitude.getItems().stream()
                    .sorted(Comparator.comparing(i->i.getCriterion().getId()))
                    .map(i->new AnnualAppraisalSnapshotDto.Criterion(i.getCriterion().getId(),i.getCriterion().getName(),
                            i.getCriterion().getDescription(),i.getSuperiorPoint())).toList());
        var snapshot=new AnnualAppraisalSnapshotDto(participant.getId(),period.getId(),participant.getStaff().getId(),
                participant.getReviewFrequency(),period.getAnnualKpiConsolidationMethod(),kpiWeight,attitudeWeight,
                annualKpi,attitudeScore,finalScore,List.copyOf(sources),attitudeSource);
        return new AnnualAppraisalReadinessDto(blockers.isEmpty(),List.copyOf(blockers),snapshot);
    }

    private KpiScoreBreakdownDto legacyBreakdown(KpiAssessment assessment) {
        var allocation=assessment.getParticipant().getEmployeeLevelConfiguration();
        var levels=Arrays.stream(KpiLevel.values()).map(level->{
            BigDecimal weight=allocation==null?null:switch(level) {
                case COMPANY->allocation.getCompanyKpiWeight();case DEPARTMENT->allocation.getDepartmentKpiWeight();
                case INDIVIDUAL->allocation.getIndividualKpiWeight();
            };
            var items=assessment.getItems().stream().filter(i->i.getAssignment().getKpi().getPlan().getLevel()==level)
                    .sorted(Comparator.comparing(KpiAssessmentItem::getId))
                    .map(i->new KpiScoreBreakdownDto.Item(plans.toDto(i.getAssignment().getKpi()),null,i.getSuperiorPoint())).toList();
            return new KpiScoreBreakdownDto.Level(level,weight,null,null,assessment.getReviewedAt(),
                    KpiScoreBreakdownDto.Availability.AVAILABLE,null,items);
        }).toList();
        return new KpiScoreBreakdownDto(assessment.getId(),assessment.getCheckpointScore(),levels);
    }

    private boolean validScore(BigDecimal score) {return score!=null && score.signum()>=0 && score.compareTo(HUNDRED)<=0;}
}
