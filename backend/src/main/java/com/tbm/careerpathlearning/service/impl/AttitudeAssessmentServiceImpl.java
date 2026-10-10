package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.math.*;

@Service @Transactional @PreAuthorize("hasAuthority('ROLE_USER')")
public class AttitudeAssessmentServiceImpl implements AttitudeAssessmentService {
    private static final Logger log=LoggerFactory.getLogger(AttitudeAssessmentServiceImpl.class);
    private static final String UNAVAILABLE_TITLE="Attitude Evaluation Not Yet Available";
    private static final String MISSING_CONFIGURATION="The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later.";
    private final AttitudeAssessmentRepository assessments;
    private final ReviewPeriodParticipantRepository participants;
    private final AnnualKpiReviewPeriodRepository periods;
    private final StaffRepository staff;
    private final AttitudeAssessmentMapper mapper;
    private final AttitudeConfigurationMapper configurations;
    private final KpiPlanMapper periodMapper;
    private final EmailService email;
    private final Clock clock;

    public AttitudeAssessmentServiceImpl(AttitudeAssessmentRepository assessments,ReviewPeriodParticipantRepository participants,
            AnnualKpiReviewPeriodRepository periods,StaffRepository staff,AttitudeAssessmentMapper mapper,
            AttitudeConfigurationMapper configurations,KpiPlanMapper periodMapper,EmailService email,
            @Qualifier("annualKpiReviewClock") Clock clock) {
        this.assessments=assessments;this.participants=participants;this.periods=periods;this.staff=staff;
        this.mapper=mapper;this.configurations=configurations;this.periodMapper=periodMapper;this.email=email;this.clock=clock;
    }

    @Override @Transactional(readOnly=true)
    public List<KpiPeriodContextDto> periods(UUID actor) {
        active(actor);
        return participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(actor).stream().map(p->{
            var dto=periodMapper.toContext(p.getReviewPeriod());
            dto.setKpiAllocation(periodMapper.toDto(p.getEmployeeLevelConfiguration()));return dto;
        }).toList();
    }
    @Override @Transactional(readOnly=true)
    public AttitudeAssessmentDto mine(Long reviewPeriodId,UUID actor) {
        active(actor);var p=participant(reviewPeriodId,actor);
        var saved=assessments.findByParticipantId(p.getId());
        if(saved.isPresent()) return details(saved.get(),actor);
        // An unavailable response or virtual Draft is read-only projection, never a GET-side insert.
        var message=configurationProblem(p);
        if(message!=null) {
            var dto=new AttitudeAssessmentDto();context(dto,p);availability(dto,message);return dto;
        }
        return details(newDraft(p,actor),actor);
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_ATTITUDE_EVALUATION')")
    public AttitudeAssessmentDto get(Long id,UUID actor) {
        var a=assessment(id,false);active(actor);
        if(!a.getParticipant().getStaff().getId().equals(actor) && !mayReview(a,actor))
            throw new AccessDeniedException("You may view only your own assessment or assessments routed from your assigned employees");
        return details(a,actor);
    }
    @Override
    public AttitudeAssessmentDto create(AttitudeAssessmentRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);
        if(request==null || request.getReviewPeriodId()==null) throw new BadRequestException("Select an Annual Review Period");
        var p=participant(request.getReviewPeriodId(),actor);
        if(assessments.findByParticipantId(p.getId()).isPresent())
            throw new BadRequestException("An Attitude Self-Assessment already exists for this review period; reopen it to continue");
        var problem=configurationProblem(p);
        if(problem!=null) throw new BadRequestException(problem);
        var a=newDraft(p,actor);writable(a,actor);apply(a,request);assessments.saveAndFlush(a);return details(a,actor);
    }
    @Override
    public AttitudeAssessmentDto update(Long id,AttitudeAssessmentRequest request,UUID actor) {
        periods.lockConfiguration();var a=assessment(id,true);writable(a,actor);
        if(request==null || !Objects.equals(request.getReviewPeriodId(),a.getReviewPeriodId()))
            throw new BadRequestException("The assessment's Annual Review Period cannot be changed");
        apply(a,request);touch(a,actor);assessments.saveAndFlush(a);return details(a,actor);
    }
    @Override
    public AttitudeAssessmentDto submit(Long id,UUID actor) {
        periods.lockConfiguration();var a=assessment(id,true);writable(a,actor);
        var blockers=blockers(a);
        if(!blockers.isEmpty()) throw new BadRequestException(String.join("; ",blockers));
        var superior=a.getParticipant().getStaff().getManager();
        a.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);a.setSubmittedAt(OffsetDateTime.now(clock));
        a.setSubmittedBy(actor);a.setSubmittedToSuperiorId(superior.getId());
        a.setSubmittedLate(today().isAfter(a.getParticipant().getReviewPeriod().getAttitudeSelfAssessmentDeadline()));
        touch(a,actor);assessments.saveAndFlush(a);
        String recipient=superior.getEmail(),employee=a.getParticipant().getStaffName(),period=a.getParticipant().getReviewPeriod().getName();
        afterCommit(()->email.sendAttitudeSelfAssessmentSubmittedEmail(recipient,employee,period,Locale.ENGLISH));
        return details(a,actor);
    }

    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_REVIEW_ATTITUDE_EVALUATION')")
    public List<AttitudeAssessmentReviewDto> reviews(Long reviewPeriodId,AttitudeAssessmentStatus status,UUID actor) {
        active(actor);requireReviewPermission();
        if(status==AttitudeAssessmentStatus.DRAFT)
            throw new BadRequestException("Only submitted or reviewed assessments appear in Team Reviews");
        var statuses=status==null?List.of(AttitudeAssessmentStatus.PENDING_REVIEW,AttitudeAssessmentStatus.REVIEWED):List.of(status);
        return assessments.findReviewAssessments(actor,statuses,reviewPeriodId).stream().filter(a->mayReview(a,actor)).map(a->{
            var dto=new AttitudeAssessmentReviewDto();var p=a.getParticipant();var period=p.getReviewPeriod();
            dto.setId(a.getId());dto.setEmployeeId(p.getStaff().getId());dto.setEmployeeName(p.getStaffName());
            dto.setRoleName(p.getRoleName());dto.setDepartmentName(p.getDepartmentName());
            dto.setReviewPeriodId(period.getId());dto.setReviewPeriodName(period.getName());dto.setReviewPeriodStatus(period.getStatus());
            dto.setEvaluationFormat(a.getEvaluationFormat());dto.setStatus(a.getStatus());
            dto.setSuperiorEvaluationDeadline(period.getSuperiorAttitudeEvaluationDeadline());
            dto.setSubmittedAt(a.getSubmittedAt());dto.setSubmittedLate(a.getSubmittedLate());
            dto.setReviewedAt(a.getReviewedAt());dto.setReviewedLate(a.getReviewedLate());dto.setAttitudeScore(a.getAttitudeScore());
            dto.setCanReview(a.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW && period.getStatus()==AnnualKpiReviewPeriodStatus.OPEN);
            dto.setSuperiorDraftSaved(superiorDraftSaved(a));dto.setSuperiorOverdue(superiorOverdue(a));return dto;
        }).toList();
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_ATTITUDE_EVALUATION')")
    public AttitudeAssessmentDto saveSuperiorDraft(Long id,AttitudeSuperiorAssessmentRequest request,UUID actor) {
        periods.lockConfiguration();var a=assessment(id,true);reviewWritable(a,actor);
        if(request==null || request.getItems()==null)
            throw new BadRequestException("Provide the Superior answers, or an empty list for an incomplete Draft");
        var byId=a.getItems().stream().collect(Collectors.toMap(AttitudeAssessmentItem::getId,x->x));
        Set<Long> supplied=new HashSet<>();
        // Validate the entire request before changing any stored answers.
        for(var answer:request.getItems()) {
            if(answer==null || answer.getItemId()==null || !supplied.add(answer.getItemId()))
                throw new BadRequestException("Each Superior answer must identify a different assessment criterion");
            if(!byId.containsKey(answer.getItemId())) throw new AccessDeniedException("This criterion does not belong to the submitted assessment");
            if(answer.getSuperiorPoint()!=null && (answer.getSuperiorPoint()<1 || answer.getSuperiorPoint()>5))
                throw new BadRequestException("Select a Superior Assessment Point from 1 to 5");
            if(answer.getSuperiorComment()!=null && answer.getSuperiorComment().length()>10000)
                throw new BadRequestException("Keep each comment within 10000 characters");
        }
        for(var answer:request.getItems()) {
            var item=byId.get(answer.getItemId());item.setSuperiorPoint(answer.getSuperiorPoint());
            item.setSuperiorComment(answer.getSuperiorComment()==null?null:answer.getSuperiorComment().strip());
        }
        touch(a,actor);assessments.saveAndFlush(a);return details(a,actor);
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_ATTITUDE_EVALUATION')")
    public AttitudeAssessmentDto completeReview(Long id,UUID actor) {
        periods.lockConfiguration();var a=assessment(id,true);reviewWritable(a,actor);
        var blockers=reviewBlockers(a);
        if(!blockers.isEmpty()) throw new BadRequestException(String.join("; ",blockers));
        // All criteria have equal weight. Self points are deliberately excluded from the official score.
        var total=a.getItems().stream().map(i->BigDecimal.valueOf(i.getSuperiorPoint())).reduce(BigDecimal.ZERO,BigDecimal::add);
        a.setAttitudeScore(total.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(a.getItems().size()).multiply(BigDecimal.valueOf(5)),4,RoundingMode.HALF_UP));
        a.setReviewedAt(OffsetDateTime.now(clock));a.setReviewedBy(actor);
        a.setReviewedLate(today().isAfter(a.getParticipant().getReviewPeriod().getSuperiorAttitudeEvaluationDeadline()));
        a.setStatus(AttitudeAssessmentStatus.REVIEWED);touch(a,actor);assessments.saveAndFlush(a);
        String recipient=a.getParticipant().getStaff().getEmail(),employee=a.getParticipant().getStaffName();
        String period=a.getParticipant().getReviewPeriod().getName();
        afterCommit(()->email.sendAttitudeAssessmentReviewedEmail(recipient,employee,period,Locale.ENGLISH));
        return details(a,actor);
    }

    private AttitudeAssessment newDraft(ReviewPeriodParticipant p,UUID actor) {
        var a=new AttitudeAssessment();a.setParticipant(p);a.setReviewPeriodId(p.getReviewPeriod().getId());
        a.setConfiguration(p.getReviewPeriod().getAttitudeConfiguration());a.setEvaluationFormat(format(p));
        a.setCreatedAt(OffsetDateTime.now(clock));a.setCreatedBy(actor);touch(a,actor);
        for(var criterion:criteria(a.getConfiguration(),a.getEvaluationFormat())) {
            var item=new AttitudeAssessmentItem();item.setAssessment(a);item.setCriterion(criterion);
            item.setConfigurationId(a.getConfiguration().getId());a.getItems().add(item);
        }
        return a;
    }
    private void apply(AttitudeAssessment a,AttitudeAssessmentRequest request) {
        if(request.getItems()==null) throw new BadRequestException("Provide the assessment answers, or an empty list for an incomplete Draft");
        var byId=a.getItems().stream().collect(Collectors.toMap(i->i.getCriterion().getId(),i->i));
        Set<Long> supplied=new HashSet<>();
        // Validate all answers first so a rejected request does not partially change a Draft.
        for(var answer:request.getItems()) {
            if(answer==null || answer.getCriterionId()==null || !supplied.add(answer.getCriterionId()))
                throw new BadRequestException("Each answer must identify a different Attitude criterion");
            if(!byId.containsKey(answer.getCriterionId())) throw new AccessDeniedException("This criterion does not belong to your Attitude Self-Assessment");
            if(answer.getSelfPoint()!=null && (answer.getSelfPoint()<1 || answer.getSelfPoint()>5))
                throw new BadRequestException("Select a Self-Assessment Point from 1 to 5");
            if(answer.getSelfComment()!=null && answer.getSelfComment().length()>10000)
                throw new BadRequestException("Keep each comment within 10000 characters");
        }
        for(var answer:request.getItems()) {
            var item=byId.get(answer.getCriterionId());item.setSelfPoint(answer.getSelfPoint());
            item.setSelfComment(answer.getSelfComment()==null?null:answer.getSelfComment().strip());
        }
    }
    private AttitudeAssessmentDto details(AttitudeAssessment a,UUID actor) {
        var dto=mapper.toDto(a);context(dto,a.getParticipant());
        if(a.getId()==null) {dto.setCreatedAt(null);dto.setUpdatedAt(null);}
        dto.setRatingDefinitions(a.getConfiguration().getRatingDefinitions().stream()
                .sorted(Comparator.comparingInt(AttitudeRatingDefinition::getPoint).reversed()).map(configurations::toDto).toList());
        dto.setItems(a.getItems().stream().sorted(Comparator.comparingInt((AttitudeAssessmentItem i)->i.getCriterion().getDisplayOrder())
                .thenComparing(i->i.getCriterion().getId())).map(i->{
                    var row=mapper.toDto(i);
                    if(a.getStatus()==AttitudeAssessmentStatus.REVIEWED || mayReview(a,actor)) {
                        row.setSuperiorPoint(i.getSuperiorPoint());row.setSuperiorComment(i.getSuperiorComment());
                    }
                    return row;
                }).toList());
        String problem=configurationProblem(a.getParticipant());
        if(problem==null && (!Objects.equals(a.getConfiguration().getId(),a.getParticipant().getReviewPeriod().getAttitudeConfiguration().getId())
                || a.getEvaluationFormat()!=format(a.getParticipant())))
            problem="The saved assessment does not match this review period's Attitude Evaluation setup. Please contact your administrator.";
        availability(dto,problem);
        dto.setSubmissionBlockers(blockers(a));
        boolean owner=a.getParticipant().getStaff().getId().equals(actor);
        dto.setCanSaveDraft(owner && a.getStatus()==AttitudeAssessmentStatus.DRAFT && dto.isAvailable());
        dto.setCanSubmit(owner && dto.getSubmissionBlockers().isEmpty());
        dto.setOverdue(a.getStatus()==AttitudeAssessmentStatus.DRAFT && dto.getSelfAssessmentDeadline()!=null && today().isAfter(dto.getSelfAssessmentDeadline()));
        boolean reviewer=mayReview(a,actor);
        dto.setSuperiorDraftSaved(reviewer && superiorDraftSaved(a));
        dto.setCanSaveSuperiorDraft(reviewer && a.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW
                && dto.getReviewPeriodStatus()==AnnualKpiReviewPeriodStatus.OPEN);
        dto.setReviewBlockers(reviewer?reviewBlockers(a):List.of());
        dto.setCanCompleteReview(reviewer && dto.getReviewBlockers().isEmpty());
        dto.setSuperiorOverdue(superiorOverdue(a));
        return dto;
    }
    private void context(AttitudeAssessmentDto dto,ReviewPeriodParticipant p) {
        var period=p.getReviewPeriod();dto.setParticipantId(p.getId());dto.setEmployeeName(p.getStaffName());
        dto.setRoleName(p.getRoleName());dto.setDepartmentName(p.getDepartmentName());dto.setReviewPeriodId(period.getId());
        dto.setReviewPeriodName(period.getName());dto.setReviewPeriodStatus(period.getStatus());
        dto.setSelfAssessmentDeadline(period.getAttitudeSelfAssessmentDeadline());
        dto.setSuperiorEvaluationDeadline(period.getSuperiorAttitudeEvaluationDeadline());
    }
    private void availability(AttitudeAssessmentDto dto,String problem) {
        if(problem==null && dto.getReviewPeriodStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            problem=dto.getReviewPeriodStatus()==AnnualKpiReviewPeriodStatus.CLOSED
                    ?"This Annual Review Period is Closed. Assessments are read-only."
                    :"Attitude Self-Assessment becomes available when the Annual Review Period opens.";
        if(problem==null && dto.getSelfAssessmentDeadline()==null)
            problem="The Attitude Self-Assessment deadline has not been configured. Please contact your administrator.";
        dto.setAvailable(problem==null);dto.setAvailabilityTitle(problem==null?null:UNAVAILABLE_TITLE);dto.setAvailabilityMessage(problem);
        if(problem!=null) dto.setSubmissionBlockers(List.of(problem));
    }
    private List<String> blockers(AttitudeAssessment a) {
        if(a.getStatus()!=AttitudeAssessmentStatus.DRAFT) return List.of("This Attitude Self-Assessment has already been submitted");
        List<String> reasons=new ArrayList<>();var period=a.getParticipant().getReviewPeriod();
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            reasons.add("Attitude Self-Assessment is available only while the Annual Review Period is Open");
        var problem=configurationProblem(a.getParticipant());if(problem!=null) reasons.add(problem);
        if(period.getAttitudeSelfAssessmentDeadline()==null) reasons.add("The Attitude Self-Assessment deadline has not been configured");
        if(problem==null) {
            if(!Objects.equals(a.getConfiguration().getId(),period.getAttitudeConfiguration().getId()) || a.getEvaluationFormat()!=format(a.getParticipant()))
                reasons.add("The assessment does not match this review period's Attitude Evaluation setup");
            var expected=criteria(a.getConfiguration(),a.getEvaluationFormat()).stream().map(AttitudeCriterion::getId).collect(Collectors.toSet());
            var actual=a.getItems().stream().map(i->i.getCriterion().getId()).collect(Collectors.toSet());
            if(!expected.equals(actual) || a.getItems().size()!=expected.size()) reasons.add("The assessment must include every applicable Attitude criterion");
        }
        for(var item:a.getItems()) if(item.getSelfPoint()==null) reasons.add("Select a Self-Assessment Point for "+item.getCriterion().getName());
        if(!validSuperior(a.getParticipant().getStaff())) reasons.add("An active immediate Superior must be assigned before submission");
        return reasons;
    }
    private String configurationProblem(ReviewPeriodParticipant p) {
        var config=p.getReviewPeriod().getAttitudeConfiguration();
        if(config==null || config.getStatus()!=AttitudeConfigurationStatus.PUBLISHED) return MISSING_CONFIGURATION;
        var format=format(p);
        if(format==null) return "An Attitude Evaluation format has not been assigned to your Job Role for this Annual Review Period. Please contact your administrator.";
        if(criteria(config,format).isEmpty()) return MISSING_CONFIGURATION;
        return null;
    }
    private AttitudeEvaluationFormat format(ReviewPeriodParticipant p) {
        if(p.getRole()==null || p.getReviewPeriod().getAttitudeConfiguration()==null) return null;
        return p.getReviewPeriod().getAttitudeConfiguration().getRoleMappings().stream()
                .filter(m->Objects.equals(m.getRole().getId(),p.getRole().getId()))
                .map(AttitudeRoleFormatMapping::getEvaluationFormat).findFirst().orElse(null);
    }
    private List<AttitudeCriterion> criteria(AttitudeConfiguration config,AttitudeEvaluationFormat format) {
        return config.getCriteria().stream().filter(AttitudeCriterion::isActive)
                .filter(c->c.getCriterionType()==AttitudeCriterionType.SHARED_CORE_VALUE || c.getEvaluationFormat()==format)
                .sorted(Comparator.comparingInt(AttitudeCriterion::getDisplayOrder).thenComparing(AttitudeCriterion::getId)).toList();
    }
    private void writable(AttitudeAssessment a,UUID actor) {
        owner(a,actor);
        if(a.getStatus()!=AttitudeAssessmentStatus.DRAFT) throw new BadRequestException("Submitted Attitude Self-Assessments are read-only");
        var dto=details(a,actor);if(!dto.isAvailable()) throw new BadRequestException(dto.getAvailabilityMessage());
    }
    private boolean reviewPermission() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("CAN_REVIEW_ATTITUDE_EVALUATION"));
    }
    private void requireReviewPermission() {
        if(!reviewPermission()) throw new AccessDeniedException("Attitude Evaluation review permission is required");
    }
    private boolean mayReview(AttitudeAssessment a,UUID actor) {
        var employee=a.getParticipant().getStaff();
        return reviewPermission() && a.getStatus()!=AttitudeAssessmentStatus.DRAFT && !employee.getId().equals(actor)
                && actor.equals(a.getSubmittedToSuperiorId()) && validSuperior(employee) && actor.equals(employee.getManager().getId());
    }
    private void reviewWritable(AttitudeAssessment a,UUID actor) {
        active(actor);requireReviewPermission();
        if(!mayReview(a,actor)) throw new AccessDeniedException("Only the routed immediate Superior may review this assessment");
        if(a.getStatus()!=AttitudeAssessmentStatus.PENDING_REVIEW) throw new BadRequestException("Only Pending Review assessments can be reviewed");
        if(a.getParticipant().getReviewPeriod().getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            throw new BadRequestException("Superior Attitude Evaluation is available only while the Annual Review Period is Open");
    }
    private List<String> reviewBlockers(AttitudeAssessment a) {
        List<String> reasons=new ArrayList<>();var period=a.getParticipant().getReviewPeriod();
        if(a.getStatus()!=AttitudeAssessmentStatus.PENDING_REVIEW) return List.of("This assessment is not awaiting Superior review");
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            reasons.add("Superior Attitude Evaluation is available only while the Annual Review Period is Open");
        if(period.getSuperiorAttitudeEvaluationDeadline()==null) reasons.add("The Superior Attitude Evaluation deadline has not been configured");
        var expected=criteria(a.getConfiguration(),a.getEvaluationFormat()).stream().map(AttitudeCriterion::getId).collect(Collectors.toSet());
        var actual=a.getItems().stream().map(i->i.getCriterion().getId()).collect(Collectors.toSet());
        if(expected.isEmpty() || !expected.equals(actual) || a.getItems().size()!=expected.size())
            reasons.add("The assessment must include every applicable Attitude criterion");
        for(var item:a.getItems()) if(item.getSuperiorPoint()==null)
            reasons.add("Select a Superior Assessment Point for "+item.getCriterion().getName());
        return reasons;
    }
    private boolean superiorDraftSaved(AttitudeAssessment a) {
        // After submission, only the routed Superior may update the assessment, including an empty Draft.
        return a.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW && a.getSubmittedToSuperiorId()!=null
                && a.getSubmittedToSuperiorId().equals(a.getUpdatedBy());
    }
    private boolean superiorOverdue(AttitudeAssessment a) {
        var deadline=a.getParticipant().getReviewPeriod().getSuperiorAttitudeEvaluationDeadline();
        return a.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW && deadline!=null && today().isAfter(deadline);
    }
    private void owner(AttitudeAssessment a,UUID actor) {
        active(actor);
        if(!a.getParticipant().getStaff().getId().equals(actor)) throw new AccessDeniedException("You may access only your own Attitude Self-Assessment");
    }
    private boolean validSuperior(Staff employee) {
        var superior=employee.getManager();return superior!=null && !superior.getId().equals(employee.getId())
                && !superior.isDeleted() && superior.getAccountStatus()==StaffAccountStatus.ACTIVE;
    }
    private Staff active(UUID actor) {
        var user=staff.findById(actor).orElseThrow(()->new AccessDeniedException("Active account required"));
        if(user.isDeleted() || user.getAccountStatus()!=StaffAccountStatus.ACTIVE) throw new AccessDeniedException("Active account required");return user;
    }
    private ReviewPeriodParticipant participant(Long periodId,UUID actor) {
        return participants.findByReviewPeriodIdAndStaffId(periodId,actor).orElseThrow(()->new AccessDeniedException("You are not enrolled in this review period"));
    }
    private AttitudeAssessment assessment(Long id,boolean lock) {
        return (lock?assessments.lockById(id):assessments.findById(id)).orElseThrow(()->new BadRequestException("Attitude Self-Assessment not found"));
    }
    private LocalDate today(){return LocalDate.now(clock);}
    private void touch(AttitudeAssessment a,UUID actor){a.setUpdatedAt(OffsetDateTime.now(clock));a.setUpdatedBy(actor);}
    private void afterCommit(Runnable action) {
        Runnable safe=()->{try {action.run();}catch(RuntimeException e){log.warn("Attitude assessment notification failed",e);}};
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit(){safe.run();}
        });else safe.run();
    }
}
