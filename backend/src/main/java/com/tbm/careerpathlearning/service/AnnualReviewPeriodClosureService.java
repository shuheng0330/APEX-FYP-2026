package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.dto.ReviewPeriodClosureDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.tbm.careerpathlearning.service.AnnualPerformanceCalculation.*;

/** Readiness never repairs sources. Closure serialises with existing performance mutations. */
@Service @Transactional
@PreAuthorize("hasAuthority('CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD')")
public class AnnualReviewPeriodClosureService {
    private final AnnualKpiReviewPeriodRepository periods;
    private final ReviewPeriodParticipantRepository participants;
    private final ReviewCheckpointRepository checkpoints;
    private final KpiAssessmentRepository assessments;
    private final AttitudeAssessmentRepository attitudes;
    private final AnnualAppraisalReadinessService performance;
    private final AnnualReviewPeriodConfigurationValidator validator;
    private final StaffRepository staff;
    private final RoleAuthorityRepository permissions;
    private final Clock clock;

    public AnnualReviewPeriodClosureService(AnnualKpiReviewPeriodRepository periods,ReviewPeriodParticipantRepository participants,
            ReviewCheckpointRepository checkpoints,KpiAssessmentRepository assessments,AttitudeAssessmentRepository attitudes,
            AnnualAppraisalReadinessService performance,AnnualReviewPeriodConfigurationValidator validator,
            StaffRepository staff,RoleAuthorityRepository permissions,@Qualifier("annualKpiReviewClock") Clock clock) {
        this.periods=periods;this.participants=participants;this.checkpoints=checkpoints;this.assessments=assessments;
        this.attitudes=attitudes;this.performance=performance;this.validator=validator;this.staff=staff;this.permissions=permissions;
        this.clock=clock.withZone(ClockConfig.ZONE);
    }
    @Transactional(readOnly=true)
    public ReviewPeriodClosureDto readiness(Long id,UUID actor) { authorise(actor);return evaluate(period(id)); }

    public ReviewPeriodClosureDto close(Long id,UUID actor) {
        periods.lockConfiguration();var admin=authorise(actor);var period=period(id);
        if(period.getStatus()==AnnualKpiReviewPeriodStatus.CLOSED) return evaluate(period);
        var ready=evaluate(period);if(!ready.canClose()) throw new ReviewPeriodClosureBlockedException(ready);
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);period.setClosedAt(OffsetDateTime.now(clock));period.setClosedBy(admin);
        period.setUpdatedAt(period.getClosedAt());period.setUpdatedBy(actor);periods.saveAndFlush(period);
        return new ReviewPeriodClosureDto(period.getId(),period.getStatus(),false,ready.earliestCloseDate(),ready.checkedAt(),
                period.getClosedAt(),admin.getName(),ready.totalEmployees(),ready.completedEmployees(),ready.kpiReviews(),ready.attitudeEvaluations(),List.of());
    }
    private AnnualKpiReviewPeriod period(Long id) {
        return periods.findById(id).orElseThrow(()->new BadRequestException("Annual Review Period not found"));
    }
    private Staff authorise(UUID actor) {
        var account=actor==null?null:staff.findById(actor).orElse(null);
        if(account==null || account.isDeleted() || account.getAccountStatus()!=StaffAccountStatus.ACTIVE || account.getRole()==null
                || account.getRole().isDeleted() || !permissions.existsByRoleIdAndAuthorityName(account.getRole().getId(),AuthorityName.CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD))
            throw new AccessDeniedException("Annual Review Period management permission is required");
        return account;
    }
    private ReviewPeriodClosureDto evaluate(AnnualKpiReviewPeriod period) {
        var issues=new ArrayList<Blocker>();LocalDate earliest=null;
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) add(issues,Code.PERIOD_NOT_OPEN,null,null,"Only an Open Review Period can be closed");
        if(period.getEndDate()==null || period.getEndDate().equals(LocalDate.MAX)) add(issues,Code.CONFIGURATION,null,null,"The Review Period End Date is missing or invalid");
        else {earliest=period.getEndDate().plusDays(1);if(LocalDate.now(clock).isBefore(earliest)) add(issues,Code.MEASUREMENT_NOT_ENDED,null,null,"Closure is available from "+earliest+", after the measurement period ends");}
        if(period.getParticipantsSnapshottedAt()==null) add(issues,Code.MISSING_ROSTER,null,null,"The publication-time participant roster is missing; administrator reconciliation is required");
        try {validator.validateDraft(period);}
        catch(IllegalArgumentException invalid) {add(issues,Code.CONFIGURATION,null,null,invalid.getMessage());}
        if(period.getStartDate()==null) add(issues,Code.CONFIGURATION,null,null,"The Review Period Start Date is missing");
        if(!validWeights(period.getKpiPerformanceWeight(),period.getAttitudeEvaluationWeight()) || period.getAnnualKpiConsolidationMethod()==null)
            add(issues,Code.CONFIGURATION,null,null,"KPI and Attitude weights must total 100% and the annual calculation method must be configured");
        var roster=participants.findAllByReviewPeriodId(period.getId());
        if(roster.isEmpty()) add(issues,Code.EMPTY_ROSTER,null,null,"There are no enrolled employees to verify");
        var ids=roster.stream().map(ReviewPeriodParticipant::getId).toList();
        var saved=new HashMap<Long,Map<Long,KpiAssessment>>();var attitudeByParticipant=new HashMap<Long,AttitudeAssessment>();
        if(!ids.isEmpty()) {
            for(var a:assessments.findPerformanceAssessments(ids)) saved.computeIfAbsent(a.getParticipant().getId(),k->new HashMap<>()).put(a.getCheckpoint().getId(),a);
            for(var a:attitudes.findPerformanceAssessments(ids)) attitudeByParticipant.put(a.getParticipant().getId(),a);
        }
        int requiredKpi=0,completedKpi=0,completedAttitude=0,completedEmployees=0;
        for(var p:roster) {
            int before=issues.size();
            var allocation=p.getEmployeeLevelConfiguration();
            try {
                if(allocation==null || allocation.getReviewPeriod()==null || !Objects.equals(allocation.getReviewPeriod().getId(),period.getId())) throw new IllegalArgumentException("Recorded KPI allocation is missing or belongs to another period");
                validator.validateLevelWeights(allocation,true);
            } catch(IllegalArgumentException invalid) {add(issues,Code.CONFIGURATION,p,null,invalid.getMessage()); }
            var attitudeProblem=AttitudeAssessmentAvailability.configurationProblem(p);
            if(attitudeProblem!=null) add(issues,Code.CONFIGURATION,p,null,attitudeProblem);
            var recordedAttitude=attitudeByParticipant.get(p.getId());
            if(recordedAttitude!=null && recordedAttitude.getConfiguration()==null) add(issues,Code.CONFIGURATION,p,null,"The recorded Attitude Evaluation configuration is missing");
            var schedule=p.getReviewFrequency()==null?List.<ReviewCheckpoint>of():checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(period.getId(),p.getReviewFrequency());
            if(schedule.isEmpty()) add(issues,Code.CONFIGURATION,p,null,"No KPI checkpoints are configured for this employee");
            requiredKpi+=schedule.size();
            AnnualAppraisalReadinessDto evaluated=null;
            if(recordedAttitude==null || recordedAttitude.getConfiguration()!=null) try {evaluated=performance.evaluate(p);}
            catch(BadRequestException invalid) {add(issues,Code.CONFIGURATION,p,null,invalid.getMessage());}
            var source=evaluated==null?null:evaluated.performance();
            for(var c:schedule) {
                int checkpointIssues=issues.size();var a=saved.getOrDefault(p.getId(),Map.of()).get(c.getId());
                if(a==null || a.getStatus()==KpiAssessmentStatus.DRAFT) add(issues,Code.KPI_SELF_ASSESSMENT,p,c,"Employee KPI Self-Assessment has not been submitted");
                else if(a.getStatus()!=KpiAssessmentStatus.REVIEWED) add(issues,Code.KPI_SUPERIOR_REVIEW,p,c,"Superior KPI review is not complete");
                else {
                    var recorded=source==null?null:source.checkpoints().stream().filter(s->Objects.equals(s.checkpointId(),c.getId())).findFirst().orElse(null);
                    var breakdown=recorded==null?null:recorded.breakdown();var score=recorded==null?null:recorded.score();
                    if(source==null) try {breakdown=performance.checkpointBreakdown(a);score=breakdown==null?null:breakdown.checkpointScore();}
                    catch(BadRequestException invalid) {add(issues,Code.CONFIGURATION,p,c,invalid.getMessage());}
                    if(breakdown==null && issues.size()==checkpointIssues) add(issues,Code.CONFIGURATION,p,c,"The recorded KPI result sources are unavailable");
                    if(breakdown!=null) for(var level:breakdown.levels()) {
                        if(level.allocation()!=null && level.allocation().signum()==0) continue;
                        if(level.status()==KpiScoreBreakdownDto.Availability.PENDING) add(issues,switch(level.level()){case COMPANY->Code.COMPANY_RESULT;case DEPARTMENT->Code.DEPARTMENT_RESULT;case INDIVIDUAL->Code.KPI_SCORE;},p,c,"Waiting for required "+level.level()+" KPI results");
                        else if(!validItems(level)) add(issues,Code.CONFIGURATION,p,c,"Recorded "+level.level()+" KPI items need complete points and weightages totalling 100%");
                    }
                    if(!validScore(score)) {if(issues.size()==checkpointIssues)add(issues,Code.KPI_SCORE,p,c,"Checkpoint KPI Score is unavailable");}
                    if(issues.size()==checkpointIssues) completedKpi++;
                }
            }
            var attitude=attitudeByParticipant.get(p.getId());
            if(attitude==null || attitude.getStatus()==AttitudeAssessmentStatus.DRAFT) add(issues,Code.ATTITUDE_SELF_ASSESSMENT,p,null,"Employee Attitude Self-Assessment has not been submitted");
            else if(attitude.getStatus()!=AttitudeAssessmentStatus.REVIEWED) add(issues,Code.ATTITUDE_SUPERIOR_REVIEW,p,null,"Superior Attitude Evaluation is not complete");
            else if(!validAttitude(p,attitude))
                add(issues,Code.ATTITUDE_SCORE,p,null,"Recorded Attitude Evaluation results or configuration are unavailable");
            else completedAttitude++;
            if(issues.size()==before && (evaluated==null || !evaluated.ready() || !validScore(source.annualKpiScore()) || !validScore(source.finalScore())))
                add(issues,Code.FINAL_SCORE,p,null,evaluated==null?"Final Performance Score is unavailable":String.join("; ",evaluated.blockers().isEmpty()?List.of("Final Performance Score is unavailable"):evaluated.blockers()));
            if(issues.size()>before && p.getStaff()!=null && (p.getStaff().isDeleted() || p.getStaff().getAccountStatus()==StaffAccountStatus.INACTIVE))
                add(issues,Code.HR_RECONCILIATION,p,null,"This enrolled employee is inactive or has left with unfinished requirements. HR reconciliation is required; the employee has not been excluded.");
            if(issues.size()==before) completedEmployees++;
        }
        return new ReviewPeriodClosureDto(period.getId(),period.getStatus(),issues.isEmpty(),earliest,OffsetDateTime.now(clock),period.getClosedAt(),
                period.getClosedBy()==null?null:period.getClosedBy().getName(),roster.size(),completedEmployees,new Completion(requiredKpi,completedKpi),new Completion(roster.size(),completedAttitude),List.copyOf(issues));
    }
    private boolean validItems(KpiScoreBreakdownDto.Level level) {
        if(level.items()==null || level.items().isEmpty()) return false;
        var total=BigDecimal.ZERO;
        for(var item:level.items()) {
            var weight=item.kpi()==null?null:item.kpi().getWeightage();
            if(!validScore(weight) || weight.stripTrailingZeros().scale()>2 || item.point()==null || item.point()<1 || item.point()>5) return false;
            total=total.add(weight);
        }
        return total.compareTo(new BigDecimal("100"))==0;
    }
    private boolean validAttitude(ReviewPeriodParticipant p,AttitudeAssessment assessment) {
        var configuration=p.getReviewPeriod().getAttitudeConfiguration();
        if(configuration==null || assessment.getConfiguration()==null || !Objects.equals(configuration.getId(),assessment.getConfiguration().getId())
                || !validScore(assessment.getAttitudeScore()) || assessment.getEvaluationFormat()==null
                || assessment.getEvaluationFormat()!=AttitudeAssessmentAvailability.format(p)) return false;
        var required=new HashSet<Long>();
        AttitudeAssessmentAvailability.criteria(configuration,assessment.getEvaluationFormat()).forEach(c->required.add(c.getId()));
        if(required.isEmpty() || required.contains(null) || assessment.getItems().size()!=required.size()) return false;
        var recorded=new HashSet<Long>();
        for(var item:assessment.getItems()) {
            if(item.getCriterion()==null || !Objects.equals(item.getConfigurationId(),configuration.getId())
                    || item.getSelfPoint()==null || item.getSelfPoint()<1 || item.getSelfPoint()>5
                    || item.getSuperiorPoint()==null || item.getSuperiorPoint()<1 || item.getSuperiorPoint()>5) return false;
            recorded.add(item.getCriterion().getId());
        }
        return recorded.equals(required);
    }
    private void add(List<Blocker> issues,Code code,ReviewPeriodParticipant p,ReviewCheckpoint c,String message) {
        issues.add(new Blocker(code,p==null?null:p.getId(),p==null?null:p.getStaffName(),p==null?null:p.getDepartmentName(),c==null?null:c.getId(),c==null?null:c.getStartDate(),c==null?null:c.getEndDate(),message));
    }
}
