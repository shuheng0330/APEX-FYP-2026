package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;

@Service @Transactional
@PreAuthorize("hasAnyAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')")
public class AnnualAppraisalServiceImpl implements AnnualAppraisalService {
    private static final String SUBMIT="CAN_SUBMIT_ANNUAL_APPRAISAL", REVIEW="CAN_REVIEW_ANNUAL_APPRAISAL";
    private static final Logger log=LoggerFactory.getLogger(AnnualAppraisalServiceImpl.class);
    private final AnnualAppraisalRepository appraisals;
    private final AnnualAppraisalActivityRepository activity;
    private final ReviewPeriodParticipantRepository participants;
    private final AnnualKpiReviewPeriodRepository periods;
    private final StaffRepository staff;
    private final AnnualAppraisalReadinessService readiness;
    private final AnnualPerformanceHistoryService performanceHistory;
    private final AnnualAppraisalMapper mapper;
    private final KpiPlanMapper plans;
    private final ObjectMapper json;
    private final EmailService email;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AnnualAppraisalServiceImpl(AnnualAppraisalRepository appraisals,AnnualAppraisalActivityRepository activity,
            ReviewPeriodParticipantRepository participants,AnnualKpiReviewPeriodRepository periods,StaffRepository staff,
            AnnualAppraisalReadinessService readiness,AnnualAppraisalMapper mapper,KpiPlanMapper plans,
            ObjectMapper json,EmailService email,@Qualifier("annualKpiReviewClock") Clock clock,
            AnnualPerformanceHistoryService performanceHistory,org.springframework.context.ApplicationEventPublisher events) {
        this.appraisals=appraisals;this.activity=activity;this.participants=participants;this.periods=periods;
        this.staff=staff;this.readiness=readiness;this.mapper=mapper;this.plans=plans;this.json=json;
        this.email=email;this.clock=clock;
        this.performanceHistory=performanceHistory;
        this.events=events;
    }

    @Override @Transactional(readOnly=true)
    public List<KpiPeriodContextDto> periods(boolean hrReview,UUID actor) {
        active(actor);permission(hrReview?REVIEW:SUBMIT);
        var available=hrReview?periods.findAllByOrderByStartDateDescIdDesc():participants.findTeamReviewPeriods(actor);
        return available.stream().filter(p->p.getStatus()!=AnnualKpiReviewPeriodStatus.DRAFT)
                .sorted(Comparator.comparing(AnnualKpiReviewPeriod::getStartDate).thenComparing(AnnualKpiReviewPeriod::getId).reversed())
                .map(plans::toContext).toList();
    }
    @Override @Transactional(readOnly=true)
    public List<AnnualAppraisalCandidateDto> team(Long periodId,UUID actor) {
        active(actor);permission(SUBMIT);period(periodId);
        return participants.findAllByReviewPeriodId(periodId).stream().filter(p->currentSuperior(p,actor))
                .map(p->{var appraisal=appraisals.findByParticipantId(p.getId()).orElse(null);
                    return new AnnualAppraisalCandidateDto(p.getId(),p.getStaff().getId(),p.getStaffName(),p.getDepartmentName(),
                            p.getRoleName(),appraisal==null?null:appraisal.getId(),appraisal==null?null:appraisal.getStatus(),
                            appraisal!=null && appraisal.getStatus()==AppraisalStatus.APPROVED
                                ?new AnnualAppraisalReadinessDto(true,List.of(),appraisal.getSnapshot()):readiness.evaluate(p));}).toList();
    }
    @Override @Transactional(readOnly=true)
    public List<AnnualAppraisalDto> reviews(Long periodId,UUID actor) {
        active(actor);permission(REVIEW);period(periodId);
        return appraisals.findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(periodId).stream()
                .filter(a->a.getStatus()!=AppraisalStatus.DRAFT).map(a->details(a,actor)).toList();
    }
    @Override @Transactional(readOnly=true)
    public AnnualAppraisalReadinessDto readiness(Long participantId,UUID actor) {
        active(actor);permission(SUBMIT);var participant=participant(participantId);
        if(!currentSuperior(participant,actor)) throw denied();
        return readiness.evaluate(participant);
    }
    @Override @Transactional(readOnly=true)
    public AnnualAppraisalDto get(Long id,UUID actor) {
        active(actor);var appraisal=appraisal(id,false);view(appraisal,actor);return details(appraisal,actor);
    }
    @Override @Transactional(readOnly=true)
    public List<AnnualAppraisalDto> history(Long participantId,UUID actor) {
        active(actor);var participant=participant(participantId);
        if(!has(REVIEW) && !(has(SUBMIT) && currentSuperior(participant,actor))) throw denied();
        return appraisals.findAllByEmployeeIdOrderBySubmittedAtDescIdDesc(participant.getStaff().getId()).stream()
                .filter(a->a.getStatus()==AppraisalStatus.APPROVED).map(a->details(a,actor)).toList();
    }

    @Override @Transactional(readOnly=true)
    public List<AnnualPerformanceHistoryDto> performanceHistory(Long participantId,UUID actor) {
        active(actor);var participant=participant(participantId);
        if(!has(REVIEW) && !(has(SUBMIT) && currentSuperior(participant,actor))) throw denied();
        return performanceHistory.read(participant);
    }

    @Override public AnnualAppraisalDto create(AnnualAppraisalRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);permission(SUBMIT);
        if(request==null || request.participantId()==null) throw new BadRequestException("Select an employee and Review Period");
        var p=participant(request.participantId());
        if(!currentSuperior(p,actor)) throw denied();
        writableParticipant(p);
        if(appraisals.findByParticipantId(p.getId()).isPresent())
            throw conflict("An appraisal already exists for this employee and Review Period. Open the existing appraisal.");
        var a=new AnnualAppraisal();a.setParticipant(p);a.setReviewPeriodId(p.getReviewPeriod().getId());
        a.setEmployeeId(p.getStaff().getId());a.setSuperiorId(actor);a.setCreatedBy(actor);a.setCreatedAt(now());
        apply(a,request);touch(a,actor);appraisals.saveAndFlush(a);record(a,"DRAFT_CREATED",actor,null);
        return details(a,actor);
    }
    @Override public AnnualAppraisalDto update(Long id,AnnualAppraisalRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);permission(SUBMIT);var a=appraisal(id,true);superiorWritable(a,actor);
        if(request==null || !Objects.equals(a.getParticipant().getId(),request.participantId()))
            throw new BadRequestException("The appraisal employee and Review Period cannot be changed");
        expected(a,request.expectedLockVersion());apply(a,request);touch(a,actor);
        appraisals.saveAndFlush(a);record(a,"DRAFT_SAVED",actor,null);return details(a,actor);
    }
    @Override public AnnualAppraisalDto submit(Long id,AnnualAppraisalActionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);permission(SUBMIT);var a=appraisal(id,true);superiorWritable(a,actor);
        expected(a,request==null?null:request.expectedLockVersion());
        var evaluated=readiness.evaluate(a.getParticipant());var blockers=submissionBlockers(a,evaluated,actor);
        if(!blockers.isEmpty()) throw new BadRequestException(String.join("; ",blockers));
        boolean returned=a.getStatus()==AppraisalStatus.RETURNED;
        a.setSnapshot(evaluated.performance());a.setSubmittedAt(now());
        a.setSubmittedLate(overdue(a.getParticipant().getReviewPeriod().getAppraisalRecommendationDeadline()));
        a.setStatus(AppraisalStatus.PENDING_REVIEW);touch(a,actor);appraisals.saveAndFlush(a);
        record(a,returned?"RESUBMITTED":"SUBMITTED",actor,null);return details(a,actor);
    }
    @Override public AnnualAppraisalDto approve(Long id,AnnualAppraisalActionRequest request,UUID actor) {
        return decide(id,request,actor,false);
    }
    @Override public AnnualAppraisalDto overrideAndApprove(Long id,AnnualAppraisalActionRequest request,UUID actor) {
        return decide(id,request,actor,true);
    }
    private AnnualAppraisalDto decide(Long id,AnnualAppraisalActionRequest request,UUID actor,boolean override) {
        periods.lockConfiguration();active(actor);permission(REVIEW);var a=appraisal(id,true);hrWritable(a,actor);
        expected(a,request==null?null:request.expectedLockVersion());
        var evaluated=readiness.evaluate(a.getParticipant());
        if(!evaluated.ready() || sourceChanged(a,evaluated))
            throw conflict("Performance results changed or are incomplete. Return the appraisal for revision before approval.");
        if(override) {
            if(request.finalDecision()==null || request.finalDecision()==a.getRecommendation())
                throw new BadRequestException("Select a different HR Final Decision, or use Approve");
            a.setFinalDecision(request.finalDecision());a.setOverrideReason(requiredText(request.reason(),"Override Reason"));
        } else {a.setFinalDecision(a.getRecommendation());a.setOverrideReason(null);}
        a.setStatus(AppraisalStatus.APPROVED);reviewed(a,actor);touch(a,actor);appraisals.saveAndFlush(a);
        record(a,override?"OVERRIDDEN":"APPROVED",actor,a.getOverrideReason());notifySuperior(a,override?"OVERRIDDEN":"APPROVED");
        return details(a,actor);
    }
    @Override public AnnualAppraisalDto returnForRevision(Long id,AnnualAppraisalActionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);permission(REVIEW);var a=appraisal(id,true);hrWritable(a,actor);
        expected(a,request==null?null:request.expectedLockVersion());
        String reason=requiredText(request.reason(),"Return Reason");
        a.setLatestReturnReason(reason);a.setReturnedRecommendation(a.getRecommendation());a.setReturnedJustification(a.getJustification());
        a.setStatus(AppraisalStatus.RETURNED);reviewed(a,actor);touch(a,actor);appraisals.saveAndFlush(a);
        record(a,"RETURNED",actor,reason);notifySuperior(a,"RETURNED");return details(a,actor);
    }

    private AnnualAppraisalDto details(AnnualAppraisal a,UUID actor) {
        var dto=mapper.toDto(a);var p=a.getParticipant();var period=p.getReviewPeriod();
        boolean approved=a.getStatus()==AppraisalStatus.APPROVED;
        var evaluated=approved?new AnnualAppraisalReadinessDto(true,List.of(),a.getSnapshot()):readiness.evaluate(p);
        dto.setReadiness(evaluated);dto.setSourceChanged(!approved && sourceChanged(a,evaluated));
        dto.setRecommendationOverdue(overdue(period.getAppraisalRecommendationDeadline()));
        dto.setHrReviewOverdue(overdue(period.getHrFinalisationDeadline()));
        var blockers=submissionBlockers(a,evaluated,actor);dto.setSubmissionBlockers(blockers);
        List<String> reviewBlockers=new ArrayList<>();
        if(a.getStatus()!=AppraisalStatus.PENDING_REVIEW) reviewBlockers.add("Only Pending Review appraisals can be reviewed");
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) reviewBlockers.add("This Review Period is read-only");
        if(!eligible(p) || !validRouting(a)) reviewBlockers.add("The employee or reporting relationship has changed; administrator review is required");
        if(Objects.equals(a.getSuperiorId(),actor)) reviewBlockers.add("You cannot review your own appraisal recommendation");
        if(Objects.equals(a.getEmployeeId(),actor)) reviewBlockers.add("You cannot review your own annual appraisal");
        boolean canReturn=has(REVIEW) && reviewBlockers.isEmpty();
        if(!evaluated.ready()) reviewBlockers.addAll(evaluated.blockers());
        if(dto.isSourceChanged()) reviewBlockers.add("Performance results changed; return for revision before approval");
        if(period.getHrFinalisationDeadline()==null) reviewBlockers.add("The HR appraisal review deadline is not configured");
        dto.setReviewBlockers(List.copyOf(reviewBlockers));
        List<String> actions=new ArrayList<>();
        if(has(SUBMIT) && Objects.equals(a.getSuperiorId(),actor) && currentSuperior(p,actor) && eligible(p)
                && period.getStatus()==AnnualKpiReviewPeriodStatus.OPEN
                && (a.getStatus()==AppraisalStatus.DRAFT || a.getStatus()==AppraisalStatus.RETURNED)) actions.add("SAVE_DRAFT");
        if(has(SUBMIT) && blockers.isEmpty()) actions.add("SUBMIT");
        if(canReturn && period.getHrFinalisationDeadline()!=null) actions.add("RETURN");
        if(has(REVIEW) && reviewBlockers.isEmpty()) {actions.add("APPROVE");actions.add("OVERRIDE_AND_APPROVE");}
        dto.setAllowedActions(List.copyOf(actions));
        dto.setActivity(activity.findAllByAppraisalIdOrderByIdAsc(a.getId()).stream().map(mapper::toDto).toList());
        return dto;
    }
    private List<String> submissionBlockers(AnnualAppraisal a,AnnualAppraisalReadinessDto evaluated,UUID actor) {
        List<String> result=new ArrayList<>();
        if(a.getStatus()!=AppraisalStatus.DRAFT && a.getStatus()!=AppraisalStatus.RETURNED) result.add("This appraisal is not editable");
        if(!Objects.equals(a.getSuperiorId(),actor) || !currentSuperior(a.getParticipant(),actor)) result.add("Only the submitting immediate Superior may submit this appraisal");
        if(!eligible(a.getParticipant())) result.add("The employee is no longer active or eligible");
        var period=a.getParticipant().getReviewPeriod();
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) result.add("Appraisals are actionable only while the Review Period is Open");
        if(period.getAppraisalRecommendationDeadline()==null) result.add("The Appraisal Recommendation Deadline is not configured");
        result.addAll(evaluated.blockers());
        if(a.getRecommendation()==null) result.add("Select a Superior Recommendation");
        if(SubmissionRevisionGuard.text(a.getJustification())==null) result.add("Enter a Recommendation Justification");
        if(a.getStatus()==AppraisalStatus.RETURNED && a.getRecommendation()==a.getReturnedRecommendation()
                && Objects.equals(SubmissionRevisionGuard.text(a.getJustification()),SubmissionRevisionGuard.text(a.getReturnedJustification())))
            result.add("Make and save a change to the recommendation or justification before resubmitting");
        return List.copyOf(result);
    }
    private void apply(AnnualAppraisal a,AnnualAppraisalRequest request) {
        a.setRecommendation(request.recommendation());a.setJustification(text(request.justification(),"Recommendation Justification"));
    }
    private void superiorWritable(AnnualAppraisal a,UUID actor) {
        if(!Objects.equals(a.getSuperiorId(),actor) || !currentSuperior(a.getParticipant(),actor)) throw denied();
        writableParticipant(a.getParticipant());
        if(a.getStatus()!=AppraisalStatus.DRAFT && a.getStatus()!=AppraisalStatus.RETURNED)
            throw conflict("This appraisal is no longer editable. Refresh to view its current status.");
    }
    private void hrWritable(AnnualAppraisal a,UUID actor) {
        writableParticipant(a.getParticipant());
        if(a.getStatus()!=AppraisalStatus.PENDING_REVIEW) throw conflict("This appraisal is no longer Pending Review. Refresh before continuing.");
        if(Objects.equals(a.getSuperiorId(),actor)) throw new AccessDeniedException("You cannot review your own recommendation");
        if(Objects.equals(a.getEmployeeId(),actor)) throw new AccessDeniedException("You cannot review your own annual appraisal");
        if(!validRouting(a)) throw conflict("The submitting Superior or reporting relationship changed. Administrator review is required.");
        if(a.getParticipant().getReviewPeriod().getHrFinalisationDeadline()==null)
            throw new BadRequestException("The HR appraisal review deadline is not configured");
    }
    private void writableParticipant(ReviewPeriodParticipant p) {
        if(p.getReviewPeriod().getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) throw new BadRequestException("This Review Period is read-only");
        if(!eligible(p)) throw new BadRequestException("The employee is no longer active or eligible");
    }
    private boolean eligible(ReviewPeriodParticipant p) {
        var employee=p.getStaff();return !employee.isDeleted() && employee.getAccountStatus()==StaffAccountStatus.ACTIVE
                && employee.getRole()!=null && !employee.getRole().isDeleted() && employee.getRole().isPerformanceReviewEligible();
    }
    private boolean currentSuperior(ReviewPeriodParticipant p,UUID actor) {
        var manager=p.getStaff().getManager();return manager!=null && Objects.equals(manager.getId(),actor)
                && !Objects.equals(p.getStaff().getId(),actor) && !manager.isDeleted() && manager.getAccountStatus()==StaffAccountStatus.ACTIVE;
    }
    private boolean validRouting(AnnualAppraisal a) {return currentSuperior(a.getParticipant(),a.getSuperiorId());}
    private boolean sourceChanged(AnnualAppraisal a,AnnualAppraisalReadinessDto current) {
        return a.getSnapshot()!=null && !json.valueToTree(a.getSnapshot()).equals(json.valueToTree(current.performance()));
    }
    private void view(AnnualAppraisal a,UUID actor) {
        if(has(REVIEW) && a.getStatus()!=AppraisalStatus.DRAFT) return;
        if(has(SUBMIT) && (Objects.equals(a.getSuperiorId(),actor) || currentSuperior(a.getParticipant(),actor))) return;
        throw denied();
    }
    private ReviewPeriodParticipant participant(Long id) {
        var p=participants.findById(id).orElseThrow(()->new BadRequestException("Review Period employee not found"));
        if(p.getReviewPeriod().getStatus()==AnnualKpiReviewPeriodStatus.DRAFT) throw new BadRequestException("Publish the Review Period first");return p;
    }
    private AnnualKpiReviewPeriod period(Long id) {
        var p=periods.findById(id).orElseThrow(()->new BadRequestException("Review Period not found"));
        if(p.getStatus()==AnnualKpiReviewPeriodStatus.DRAFT) throw new BadRequestException("Publish the Review Period first");return p;
    }
    private AnnualAppraisal appraisal(Long id,boolean lock) {
        return (lock?appraisals.lockById(id):appraisals.findById(id)).orElseThrow(()->new BadRequestException("Annual appraisal not found"));
    }
    private void expected(AnnualAppraisal a,Long expected) {
        if(expected==null || expected.longValue()!=a.getLockVersion()) throw conflict("The appraisal changed. Refresh before continuing; your entries have not been saved.");
    }
    private void record(AnnualAppraisal a,String action,UUID actor,String reason) {
        var event=new AnnualAppraisalActivity();event.setAppraisalId(a.getId());event.setAction(action);event.setActorId(actor);
        event.setOccurredAt(now());event.setReason(reason);event.setRecommendation(a.getRecommendation());event.setJustification(a.getJustification());
        event.setFinalDecision(a.getFinalDecision());
        if(!action.startsWith("DRAFT")) event.setSnapshot(a.getSnapshot());
        activity.save(event);
        if(!action.startsWith("DRAFT")) events.publishEvent(new KpiNotificationEvents.Appraisal(a,event,actor));
    }
    private void reviewed(AnnualAppraisal a,UUID actor) {
        a.setReviewedAt(now());a.setReviewedBy(actor);a.setReviewedLate(overdue(a.getParticipant().getReviewPeriod().getHrFinalisationDeadline()));
    }
    private void notifySuperior(AnnualAppraisal a,String decision) {
        var recipient=staff.findById(a.getSuperiorId()).map(Staff::getEmail).orElse(null);
        if(recipient==null) return;
        String employee=a.getParticipant().getStaffName(),period=a.getParticipant().getReviewPeriod().getName();
        afterCommit(()->email.sendAnnualAppraisalReviewedEmail(recipient,employee,period,decision,Locale.ENGLISH));
    }
    private void afterCommit(Runnable action) {
        if(!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {try {action.run();} catch(Exception failure) {log.warn("Annual appraisal notification failed",failure);}}
        });
    }
    private String text(String value,String label) {
        if(value!=null && value.length()>10000) throw new BadRequestException(label+" must contain at most 10,000 characters");
        return SubmissionRevisionGuard.text(value);
    }
    private String requiredText(String value,String label) {
        String result=text(value,label);if(result==null) throw new BadRequestException(label+" is required");return result;
    }
    private void active(UUID actor) {
        var account=staff.findById(actor).orElseThrow(AnnualAppraisalServiceImpl::denied);
        if(account.isDeleted() || account.getAccountStatus()!=StaffAccountStatus.ACTIVE) throw denied();
    }
    private static boolean has(String authority) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication!=null && authentication.getAuthorities().stream().anyMatch(a->authority.equals(a.getAuthority()));
    }
    private void permission(String authority) {if(!has(authority)) throw denied();}
    private static AccessDeniedException denied() {return new AccessDeniedException("You are not authorised to access this annual appraisal");}
    private KpiVersionConflictException conflict(String message) {return new KpiVersionConflictException(message);}
    private void touch(AnnualAppraisal a,UUID actor) {a.setUpdatedAt(now());a.setUpdatedBy(actor);}
    private boolean overdue(LocalDate deadline) {return deadline!=null && LocalDate.now(clock).isAfter(deadline);}
    private OffsetDateTime now() {return OffsetDateTime.now(clock);}
}
