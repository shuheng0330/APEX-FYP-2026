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
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @Transactional
public class KpiAssessmentServiceImpl implements KpiAssessmentService {
    private static final Logger log=LoggerFactory.getLogger(KpiAssessmentServiceImpl.class);
    private final KpiAssessmentRepository assessments;
    private final KpiAssessmentItemRepository items;
    private final KpiAssessmentEvidenceRepository files;
    private final ReviewPeriodParticipantRepository participants;
    private final ReviewCheckpointRepository checkpoints;
    private final EmployeeKpiAssignmentRepository assignments;
    private final AnnualKpiReviewPeriodRepository periods;
    private final StaffRepository staff;
    private final KpiPlanMapper kpis;
    private final KpiAssessmentMapper mapper;
    private final KpiAssessmentEvidenceStorage storage;
    private final EmailService email;
    private final Clock clock;

    public KpiAssessmentServiceImpl(KpiAssessmentRepository assessments,KpiAssessmentItemRepository items,
            KpiAssessmentEvidenceRepository files,ReviewPeriodParticipantRepository participants,
            ReviewCheckpointRepository checkpoints,EmployeeKpiAssignmentRepository assignments,
            AnnualKpiReviewPeriodRepository periods,StaffRepository staff,KpiPlanMapper kpis,
            KpiAssessmentMapper mapper,KpiAssessmentEvidenceStorage storage,EmailService email,
            @Qualifier("annualKpiReviewClock") Clock clock) {
        this.assessments=assessments;this.items=items;this.files=files;this.participants=participants;
        this.checkpoints=checkpoints;this.assignments=assignments;this.periods=periods;this.staff=staff;
        this.kpis=kpis;this.mapper=mapper;this.storage=storage;this.email=email;this.clock=clock;
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPeriodContextDto> periods(UUID actor) {
        active(actor);
        return participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(actor).stream().map(p->{
            var dto=kpis.toContext(p.getReviewPeriod());dto.setKpiAllocation(kpis.toDto(p.getEmployeeLevelConfiguration()));return dto;
        }).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiAssessmentCheckpointDto> checkpoints(Long periodId,UUID actor) {
        active(actor);var participant=participant(periodId,actor);
        var saved=assessments.findAllByParticipantId(participant.getId()).stream()
                .collect(Collectors.toMap(a->a.getCheckpoint().getId(),a->a));
        return checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(periodId,participant.getReviewFrequency())
                .stream().map(c->checkpointDto(c,saved.get(c.getId()))).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiAssessmentDto mine(Long checkpointId,UUID actor) {
        active(actor);var checkpoint=checkpoint(checkpointId);var participant=participant(checkpoint.getReviewPeriod().getId(),actor);
        requireFrequency(participant,checkpoint);
        var saved=assessments.findByParticipantIdAndCheckpointId(participant.getId(),checkpointId);
        if(saved.isPresent()) return details(saved.get(),actor);
        // A virtual Draft exposes availability/readiness without inserting anything on a GET.
        return details(newDraft(participant,checkpoint,actor),actor);
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_KPI_ASSESSMENT')")
    public KpiAssessmentDto get(Long id,UUID actor) {var a=assessment(id,false);readAccess(a,actor);return details(a,actor);}

    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiAssessmentDto create(KpiAssessmentRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);
        if(request==null || request.getCheckpointId()==null) throw new BadRequestException("Select a Review Checkpoint");
        var checkpoint=checkpoint(request.getCheckpointId());var participant=participant(checkpoint.getReviewPeriod().getId(),actor);
        requireFrequency(participant,checkpoint);
        if(assessments.findByParticipantIdAndCheckpointId(participant.getId(),checkpoint.getId()).isPresent())
            throw new BadRequestException("An assessment already exists for this checkpoint; reopen it to continue");
        var a=newDraft(participant,checkpoint,actor);writable(a,actor);
        apply(a,request);assessments.saveAndFlush(a);return details(a,actor);
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiAssessmentDto update(Long id,KpiAssessmentRequest request,UUID actor) {
        periods.lockConfiguration();var a=assessment(id,true);writable(a,actor);
        if(request==null || !Objects.equals(request.getCheckpointId(),a.getCheckpoint().getId()))
            throw new BadRequestException("The assessment checkpoint cannot be changed");
        apply(a,request);touch(a,actor);assessments.saveAndFlush(a);return details(a,actor);
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiAssessmentDto submit(Long id,UUID actor) {
        periods.lockConfiguration();var a=assessment(id,true);writable(a,actor);
        reconcile(a);
        var blockers=blockers(a,a.getItems());
        if(!blockers.isEmpty()) throw new BadRequestException(String.join("; ",blockers));
        var superior=a.getParticipant().getStaff().getManager();
        a.setStatus(KpiAssessmentStatus.PENDING_REVIEW);a.setSubmittedAt(OffsetDateTime.now(clock));
        a.setSubmittedBy(actor);a.setSubmittedToSuperiorId(superior.getId());
        a.setSubmittedLate(today().isAfter(a.getCheckpoint().getSelfAssessmentDeadline()));touch(a,actor);
        assessments.saveAndFlush(a);
        String recipient=superior.getEmail(),employee=a.getParticipant().getStaffName();
        String period=a.getParticipant().getReviewPeriod().getName(),checkpoint=a.getCheckpoint().getEndDate().toString();
        afterCommit(()->email.sendKpiSelfAssessmentSubmittedEmail(recipient,employee,period,checkpoint,Locale.ENGLISH));
        return details(a,actor);
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiAssessmentEvidenceDto upload(Long itemId,MultipartFile file,UUID actor) {
        periods.lockConfiguration();var item=item(itemId);var a=assessment(item.getAssessment().getId(),true);writable(a,actor);
        var stored=storage.store(file);
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED) safeDelete(stored.key());}
            });
        try {
            var evidence=new KpiAssessmentEvidence();evidence.setItem(item);evidence.setStorageKey(stored.key());
            evidence.setOriginalFilename(stored.filename());evidence.setContentType(stored.contentType());evidence.setSizeBytes(stored.size());
            evidence.setUploadedBy(actor);evidence.setUploadedAt(OffsetDateTime.now(clock));
            files.saveAndFlush(evidence);touch(a,actor);assessments.saveAndFlush(a);
            return mapper.toDto(evidence);
        } catch(RuntimeException e) {safeDelete(stored.key());throw e;}
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_KPI_ASSESSMENT')")
    public List<KpiAssessmentEvidenceDto> evidence(Long itemId,UUID actor) {
        var item=item(itemId);readAccess(item.getAssessment(),actor);return item.getEvidence().stream().map(mapper::toDto).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_KPI_ASSESSMENT')")
    public Download download(Long evidenceId,UUID actor) {
        var evidence=file(evidenceId);readAccess(evidence.getItem().getAssessment(),actor);
        return new Download(storage.resource(evidence.getStorageKey()),mapper.toDto(evidence));
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public void deleteEvidence(Long evidenceId,UUID actor) {
        periods.lockConfiguration();var evidence=file(evidenceId);var a=assessment(evidence.getItem().getAssessment().getId(),true);
        writable(a,actor);files.delete(evidence);files.flush();touch(a,actor);assessments.saveAndFlush(a);
        afterCommit(()->safeDelete(evidence.getStorageKey()));
    }
    private void apply(KpiAssessment a,KpiAssessmentRequest request) {
        if(request.getItems()==null) throw new BadRequestException("Provide the assessment answers, or an empty list for an incomplete Draft");
        var available=applicable(a.getParticipant());
        var byId=available.stream().collect(Collectors.toMap(EmployeeKpiAssignment::getId,x->x));
        Set<Long> supplied=new HashSet<>();
        for(var answer:request.getItems()) {
            if(answer==null || answer.getAssignmentId()==null || !supplied.add(answer.getAssignmentId()))
                throw new BadRequestException("Each assessment answer must identify a different assigned KPI");
            if(!byId.containsKey(answer.getAssignmentId())) throw new AccessDeniedException("This KPI is not assigned to your assessment");
            if(answer.getSelfPoint()!=null && (answer.getSelfPoint()<1 || answer.getSelfPoint()>5))
                throw new BadRequestException("Select a Self-Assessment Point from 1 to 5");
            if(answer.getSelfComment()!=null && answer.getSelfComment().length()>10000)
                throw new BadRequestException("Keep each comment within 10000 characters");
        }
        reconcile(a,available);
        var saved=a.getItems().stream().collect(Collectors.toMap(i->i.getAssignment().getId(),i->i));
        for(var answer:request.getItems()) {
            var item=saved.get(answer.getAssignmentId());item.setSelfPoint(answer.getSelfPoint());
            item.setSelfComment(answer.getSelfComment()==null?null:answer.getSelfComment().strip());
        }
    }
    private void reconcile(KpiAssessment a) {reconcile(a,applicable(a.getParticipant()));}
    private void reconcile(KpiAssessment a,List<EmployeeKpiAssignment> available) {
        var existing=a.getItems().stream().map(i->i.getAssignment().getId()).collect(Collectors.toSet());
        for(var assignment:available) if(!existing.contains(assignment.getId())) a.getItems().add(newItem(a,assignment));
    }
    private KpiAssessmentItem newItem(KpiAssessment a,EmployeeKpiAssignment assignment) {
        var item=new KpiAssessmentItem();item.setAssessment(a);item.setAssignment(assignment);
        item.setParticipantId(a.getParticipant().getId());item.setReviewPeriodId(a.getReviewPeriodId());return item;
    }
    private List<EmployeeKpiAssignment> applicable(ReviewPeriodParticipant participant) {
        return assignments.findAllByParticipantId(participant.getId()).stream()
                .filter(x->Objects.equals(x.getReviewPeriodId(),participant.getReviewPeriod().getId()))
                .filter(x->confirmed(x.getKpi().getPlan()))
                .sorted(Comparator.comparing(EmployeeKpiAssignment::getId)).toList();
    }
    private boolean confirmed(KpiPlan plan) {
        return plan.getLevel()==KpiLevel.COMPANY?plan.getStatus()==KpiPlanStatus.PUBLISHED:plan.getStatus()==KpiPlanStatus.APPROVED;
    }
    private List<KpiLevel> missingLevels(KpiAssessment a,List<KpiAssessmentItem> included) {
        var allocation=a.getParticipant().getEmployeeLevelConfiguration();
        if(allocation==null) return List.of(KpiLevel.COMPANY,KpiLevel.DEPARTMENT,KpiLevel.INDIVIDUAL);
        var assignedIds=included.stream().map(i->i.getAssignment().getKpi().getId()).collect(Collectors.toSet());
        var plans=included.stream().map(i->i.getAssignment().getKpi().getPlan()).distinct().toList();
        return Arrays.stream(KpiLevel.values()).filter(level->{
            var weight=switch(level) {
                case COMPANY->allocation.getCompanyKpiWeight();case DEPARTMENT->allocation.getDepartmentKpiWeight();
                case INDIVIDUAL->allocation.getIndividualKpiWeight();
            };
            if(weight!=null && weight.signum()==0) return false;
            return plans.stream().noneMatch(p->p.getLevel()==level && confirmed(p) && !p.getItems().isEmpty()
                    && p.getItems().stream().allMatch(k->assignedIds.contains(k.getId())));
        }).toList();
    }
    private List<String> blockers(KpiAssessment a,List<KpiAssessmentItem> included) {
        List<String> reasons=new ArrayList<>();
        if(a.getStatus()!=KpiAssessmentStatus.DRAFT) {reasons.add("This assessment has already been submitted");return reasons;}
        if(a.getParticipant().getReviewPeriod().getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            reasons.add("Self-Assessment is available only while the Annual Review Period is Open");
        if(today().isBefore(a.getCheckpoint().getEndDate().plusDays(1))) reasons.add("This checkpoint is not yet available for assessment");
        for(var level:missingLevels(a,included)) reasons.add(switch(level) {
            case COMPANY->"Company KPIs are not yet published or fully assigned";
            case DEPARTMENT->"Department KPIs are not yet approved or fully assigned";
            case INDIVIDUAL->"Individual KPIs are not yet approved or fully assigned";
        });
        if(included.isEmpty()) reasons.add("No confirmed KPIs are currently assigned to this assessment");
        for(var item:included) if(item.getSelfPoint()==null) reasons.add("Select a Self-Assessment Point for "+item.getAssignment().getKpi().getName());
        if(!validSuperior(a.getParticipant().getStaff())) reasons.add("An active immediate Superior must be assigned before submission");
        return reasons;
    }
    private KpiAssessmentDto details(KpiAssessment a,UUID actor) {
        var dto=mapper.toDto(a);var participant=a.getParticipant();var period=participant.getReviewPeriod();
        if(a.getId()==null) {dto.setCreatedAt(null);dto.setUpdatedAt(null);}
        dto.setParticipantId(participant.getId());dto.setEmployeeName(participant.getStaffName());dto.setReviewPeriodId(period.getId());
        dto.setRoleName(participant.getRoleName());
        dto.setReviewPeriodName(period.getName());dto.setReviewPeriodStatus(period.getStatus());
        dto.setCheckpoint(checkpointDto(a.getCheckpoint(),a.getId()==null?null:a));
        dto.setKpiAllocation(kpis.toDto(participant.getEmployeeLevelConfiguration()));
        // Project newly assigned items for Drafts only; preserve the frozen submitted set.
        var included=new ArrayList<>(a.getItems());
        if(a.getStatus()==KpiAssessmentStatus.DRAFT) {
            var present=included.stream().map(i->i.getAssignment().getId()).collect(Collectors.toSet());
            for(var assigned:applicable(participant)) if(!present.contains(assigned.getId())) included.add(newItem(a,assigned));
        }
        dto.setItems(included.stream().map(i->{
            var row=new KpiAssessmentItemDto();row.setId(i.getId());row.setAssignmentId(i.getAssignment().getId());
            row.setLevel(i.getAssignment().getKpi().getPlan().getLevel());row.setKpi(kpis.toDto(i.getAssignment().getKpi()));
            row.setSelfPoint(i.getSelfPoint());row.setSelfComment(i.getSelfComment());
            if(a.getStatus()==KpiAssessmentStatus.REVIEWED) {row.setSuperiorPoint(i.getSuperiorPoint());row.setSuperiorComment(i.getSuperiorComment());}
            row.setEvidence(i.getEvidence().stream().map(mapper::toDto).toList());return row;
        }).toList());
        dto.setMissingLevels(missingLevels(a,included));dto.setSubmissionBlockers(blockers(a,included));
        boolean owner=participant.getStaff().getId().equals(actor);
        dto.setCanSaveDraft(owner && a.getStatus()==KpiAssessmentStatus.DRAFT && dto.getCheckpoint().isAvailable());
        dto.setCanSubmit(owner && dto.getSubmissionBlockers().isEmpty());
        dto.setOverdue(a.getStatus()==KpiAssessmentStatus.DRAFT && today().isAfter(a.getCheckpoint().getSelfAssessmentDeadline()));
        return dto;
    }
    private KpiAssessmentCheckpointDto checkpointDto(ReviewCheckpoint c,KpiAssessment a) {
        var dto=new KpiAssessmentCheckpointDto();dto.setId(c.getId());dto.setReviewFrequency(c.getReviewFrequency());
        dto.setSequenceNumber(c.getSequenceNumber());dto.setStartDate(c.getStartDate());dto.setEndDate(c.getEndDate());
        dto.setAvailableFrom(c.getEndDate().plusDays(1));dto.setSelfAssessmentDeadline(c.getSelfAssessmentDeadline());
        dto.setSuperiorAssessmentDeadline(c.getSuperiorAssessmentDeadline());
        dto.setAvailable(c.getReviewPeriod().getStatus()==AnnualKpiReviewPeriodStatus.OPEN && !today().isBefore(dto.getAvailableFrom()));
        dto.setOverdue((a==null || a.getStatus()==KpiAssessmentStatus.DRAFT) && today().isAfter(c.getSelfAssessmentDeadline()));
        if(a!=null) {dto.setAssessmentId(a.getId());dto.setAssessmentStatus(a.getStatus());}return dto;
    }
    private KpiAssessment newDraft(ReviewPeriodParticipant p,ReviewCheckpoint c,UUID actor) {
        var a=new KpiAssessment();a.setParticipant(p);a.setCheckpoint(c);a.setReviewPeriodId(p.getReviewPeriod().getId());
        a.setReviewFrequency(p.getReviewFrequency());a.setCreatedAt(OffsetDateTime.now(clock));a.setCreatedBy(actor);touch(a,actor);return a;
    }
    private void writable(KpiAssessment a,UUID actor) {
        active(actor);
        if(!a.getParticipant().getStaff().getId().equals(actor)) throw new AccessDeniedException("You can edit only your own Self-Assessment");
        if(a.getStatus()!=KpiAssessmentStatus.DRAFT) throw new BadRequestException("Submitted Self-Assessments are read-only");
        if(a.getParticipant().getReviewPeriod().getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            throw new BadRequestException("Self-Assessment is available only while the Annual Review Period is Open");
        if(today().isBefore(a.getCheckpoint().getEndDate().plusDays(1))) throw new BadRequestException("This checkpoint is not yet available for assessment");
    }
    private void readAccess(KpiAssessment a,UUID actor) {
        active(actor);var employee=a.getParticipant().getStaff();
        if(employee.getId().equals(actor)) return;
        var auth=SecurityContextHolder.getContext().getAuthentication();
        boolean permitted=auth!=null && auth.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("CAN_REVIEW_KPI_ASSESSMENT"));
        if(!permitted || a.getStatus()==KpiAssessmentStatus.DRAFT || !actor.equals(a.getSubmittedToSuperiorId())
                || !validSuperior(employee) || !actor.equals(employee.getManager().getId()))
            throw new AccessDeniedException("You may view only assessments routed from your assigned employees");
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
    private ReviewCheckpoint checkpoint(Long id) {return checkpoints.findById(id).orElseThrow(()->new BadRequestException("Review Checkpoint not found"));}
    private void requireFrequency(ReviewPeriodParticipant p,ReviewCheckpoint c) {
        if(p.getReviewFrequency()!=c.getReviewFrequency()) throw new AccessDeniedException("This checkpoint does not match your review frequency");
    }
    private KpiAssessment assessment(Long id,boolean lock) {return (lock?assessments.lockById(id):assessments.findById(id)).orElseThrow(()->new BadRequestException("Assessment not found"));}
    private KpiAssessmentItem item(Long id) {return items.findById(id).orElseThrow(()->new BadRequestException("Assessment KPI not found"));}
    private KpiAssessmentEvidence file(Long id) {return files.findById(id).orElseThrow(()->new BadRequestException("Evidence not found"));}
    private LocalDate today() {return LocalDate.now(clock);}
    private void touch(KpiAssessment a,UUID actor) {a.setUpdatedAt(OffsetDateTime.now(clock));a.setUpdatedBy(actor);}
    private void afterCommit(Runnable action) {
        Runnable safe=()->{try {action.run();}catch(RuntimeException e){log.warn("Assessment post-commit action failed",e);}};
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit(){safe.run();}
        });else safe.run();
    }
    private void safeDelete(String key) {try {storage.delete(key);}catch(RuntimeException e){log.warn("Could not clean up assessment evidence",e);}}
}
