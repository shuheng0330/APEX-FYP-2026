package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;

@Service @Transactional
public class KpiRevisionService {
    public static final String ACCESS="CAN_MANAGE_KPI_AMENDMENTS", COMPANY="CAN_REVISE_COMPANY_KPI",
        HR="CAN_PREPARE_DEPARTMENT_KPI_REVISION", MD="CAN_APPROVE_DEPARTMENT_KPI", HOD="CAN_MANAGE_DEPARTMENT_KPI",
        INDIVIDUAL="CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS", SUPERIOR="CAN_REVIEW_INDIVIDUAL_KPI";
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(KpiRevisionService.class);
    private final KpiPlanVersionRepository versions;
    private final KpiPlanRepository plans;
    private final AnnualKpiReviewPeriodRepository periods;
    private final StaffRepository staff;
    private final ReviewPeriodParticipantRepository participants;
    private final EmployeeKpiAssignmentRepository employeeAssignments;
    private final PerformanceDepartmentResolver departments;
    private final KpiPlanMapper items;
    private final KpiRevisionMapper mapper;
    private final KpiPlanValidator validator;
    private final KpiAssignmentService assignments;
    private final RoleAuthorityRepository roleAuthorities;
    private final EmailService email;
    private final Clock clock;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    public KpiRevisionService(KpiPlanVersionRepository versions,KpiPlanRepository plans,AnnualKpiReviewPeriodRepository periods,
            StaffRepository staff,ReviewPeriodParticipantRepository participants,EmployeeKpiAssignmentRepository employeeAssignments,
            PerformanceDepartmentResolver departments,KpiPlanMapper items,KpiRevisionMapper mapper,KpiPlanValidator validator,
            KpiAssignmentService assignments,RoleAuthorityRepository roleAuthorities,EmailService email,
            @Qualifier("annualKpiReviewClock") Clock clock) {
        this.versions=versions;this.plans=plans;this.periods=periods;this.staff=staff;this.participants=participants;
        this.employeeAssignments=employeeAssignments;this.departments=departments;this.items=items;this.mapper=mapper;
        this.validator=validator;this.assignments=assignments;this.roleAuthorities=roleAuthorities;this.email=email;this.clock=clock;
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiPeriodContextDto> periods(UUID actor) {
        active(actor);
        return periods.findAllByOrderByStartDateDescIdDesc().stream().filter(p->p.getStatus()!=AnnualKpiReviewPeriodStatus.DRAFT)
            .filter(p->has(ACCESS)||has(COMPANY)||has(HR)||has(MD)||has(INDIVIDUAL)
                ||Arrays.stream(KpiLevel.values()).flatMap(level->plans.findAllByLevelOrderByUpdatedAtDesc(level).stream())
                    .anyMatch(plan->plan.getReviewPeriod().getId().equals(p.getId())&&operationalRead(plan,actor)))
            .map(items::toContext).toList();
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_KPI_AMENDMENTS')")
    public KpiAmendmentAccessDto access(Long periodId,UUID actor) {active(actor);return accessDto(period(periodId));}
    @PreAuthorize("hasAuthority('CAN_MANAGE_KPI_AMENDMENTS')")
    public KpiAmendmentAccessDto updateAccess(Long periodId,KpiAmendmentAccessRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var period=period(periodId);publishedWritable(period);
        if(request==null || request.enabled()==null) throw new BadRequestException("Select whether KPI amendments are enabled");
        period.setKpiAmendmentsEnabled(request.enabled());period.setKpiAmendmentsCloseDate(request.closeDate());
        period.setKpiAmendmentsChangedBy(actor);period.setKpiAmendmentsChangedAt(now());periods.saveAndFlush(period);
        return accessDto(period);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiPlanDto> eligiblePlans(Long periodId,UUID actor) {
        active(actor);
        return Arrays.stream(KpiLevel.values())
            .flatMap(level->plans.findAllByLevelOrderByUpdatedAtDesc(level).stream())
            .filter(p->Objects.equals(periodId,p.getReviewPeriod().getId()) && confirmed(p) && operationalRead(p,actor))
            .map(items::toDto).toList();
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiRevisionDto> queue(Long periodId,KpiLevel level,KpiVersionStatus status,UUID actor) {
        active(actor);
        return versions.revisions().stream().filter(v->operationalRead(v.getPlan(),actor)&&revisionRouteReadable(v,actor))
            .filter(v->periodId==null||periodId.equals(v.getReviewPeriodId()))
            .filter(v->level==null||level==v.getLevel()).filter(v->status==null||status==v.getStatus())
            .map(v->details(v,actor,true)).toList();
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER') or hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto get(Long id,UUID actor) {
        active(actor);var v=version(id,false);read(v,actor);return details(v,actor,operationalRead(v.getPlan(),actor));
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER') or hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiRevisionDto> history(Long planId,UUID actor) {
        active(actor);var plan=plans.findById(planId).orElseThrow(()->new BadRequestException("KPI plan not found"));
        boolean internal=operationalRead(plan,actor);
        if(!internal && !employeeRead(plan,actor)) throw new AccessDeniedException("This KPI plan is not available to you");
        return versions.findAllByPlanIdOrderByVersionNumberDesc(planId).stream()
            .filter(v->(internal&&revisionRouteReadable(v,actor)) || effective(v))
            .map(v->details(v,actor,internal&&revisionRouteReadable(v,actor))).toList();
    }
    @PreAuthorize("hasAuthority('CAN_REVISE_COMPANY_KPI')")
    public KpiRevisionDto startCompany(Long planId,KpiRevisionRequest request,UUID actor) {return start(planId,KpiLevel.COMPANY,request,actor);}
    @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiRevisionDto requestDepartment(Long planId,KpiRevisionRequest request,UUID actor) {return start(planId,KpiLevel.DEPARTMENT,request,actor);}
    @PreAuthorize("hasAuthority('CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS')")
    public KpiRevisionDto requestIndividual(Long planId,KpiRevisionRequest request,UUID actor) {return start(planId,KpiLevel.INDIVIDUAL,request,actor);}
    private KpiRevisionDto start(Long planId,KpiLevel level,KpiRevisionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);
        var plan=plans.lockById(planId).orElseThrow(()->new BadRequestException("KPI plan not found"));
        if(plan.getLevel()!=level || !confirmed(plan) || plan.getCurrentVersion()==null)
            throw new BadRequestException("Select a confirmed KPI plan");
        permission(level==KpiLevel.COMPANY?COMPANY:level==KpiLevel.DEPARTMENT?HOD:INDIVIDUAL);
        if(level==KpiLevel.DEPARTMENT) hod(plan,actor);
        window(plan.getReviewPeriod());var reason=reason(request);
        Staff superior=level==KpiLevel.INDIVIDUAL?currentSuperior(plan):null;
        var previous=versions.findAllByPlanIdOrderByVersionNumberDesc(planId);
        if(previous.stream().anyMatch(v->v.getVersionNumber()>1 && v.getStatus().unfinished()))
            throw new KpiVersionConflictException("An unfinished revision already exists; continue or cancel it before requesting another");
        var base=plan.getCurrentVersion();
        if(base.getVersionNumber()==1) {
            base.setLatestReturnReason(plan.getLatestReturnReason());base.setLatestReturnedAt(plan.getLatestReturnedAt());
            base.setLatestReturnedBy(plan.getLatestReturnedBy());base.setLatestReturnItems(plan.getLatestReturnItems());
            base.setApprovedAt(plan.getReviewedAt());base.setApprovedBy(plan.getReviewedBy());
        }
        var v=new KpiPlanVersion();v.setPlan(plan);v.setReviewPeriodId(plan.getReviewPeriod().getId());v.setLevel(level);
        v.setVersionNumber(previous.stream().mapToInt(KpiPlanVersion::getVersionNumber).max().orElse(1)+1);
        v.setBaseVersion(base);v.setRevisionReason(reason);v.setCreatedAt(now());v.setUpdatedAt(now());v.setCreatedBy(actor);v.setUpdatedBy(actor);
        v.setStatus(level==KpiLevel.COMPANY?KpiVersionStatus.DRAFT:KpiVersionStatus.REQUESTED);
        if(level==KpiLevel.INDIVIDUAL) {
            v.setRequestedBy(actor);v.setRevisionSuperiorId(superior.getId());
            v.setPreparationMode(actor.equals(superior.getId())||request.getPreparationMode()==null
                ?KpiRevisionPreparationMode.SUPERIOR:request.getPreparationMode());
        } else if(level==KpiLevel.DEPARTMENT) v.setRequestedBy(actor);
        else {v.setPreparedBy(actor);v.setPreparedAt(now());}
        for(var k:base.getItems()) {
            var copy=new Kpi();copy.setPlan(plan);copy.setPlanId(plan.getId());copy.setReviewPeriodId(v.getReviewPeriodId());
            copy.setVersion(v);copy.setPredecessorId(k.getId());items.update(items.toDto(k),copy);v.getItems().add(copy);
        }
        event(v,"CREATED",actor,null,v.getStatus(),reason);versions.saveAndFlush(v);
        if(superior!=null) notifySuperior(v,superior,false);
        return details(v,actor,true);
    }
    @PreAuthorize("hasAnyAuthority('CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto prepare(Long id,KpiRevisionRequest request,UUID actor) {
        var v=writing(id,request,actor);requireState(v,KpiVersionStatus.REQUESTED);
        if(v.getLevel()==KpiLevel.INDIVIDUAL) individualPreparer(v,actor);
        else {permission(HR);if(v.getLevel()!=KpiLevel.DEPARTMENT || actor.equals(v.getRequestedBy())) deny();}
        v.setPreparedBy(actor);v.setPreparedAt(now());transition(v,"PREPARED",actor,KpiVersionStatus.DRAFT,null);
        return save(v,actor);
    }
    @PreAuthorize("hasAnyAuthority('CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto update(Long id,KpiRevisionRequest request,UUID actor) {
        var v=writing(id,request,actor);editor(v,actor);editable(v);
        validator.validate(request.getItems(),false);
        var before=v.getItems().stream().map(items::toDto).toList();
        var existing=new HashMap<Long,Kpi>();v.getItems().forEach(k->existing.put(k.getId(),k));
        Set<Long> seen=new HashSet<>();var replacement=new ArrayList<Kpi>();
        for(var input:request.getItems()) {
            var k=input.getId()==null?new Kpi():existing.get(input.getId());
            if(k==null || (input.getId()!=null && !seen.add(input.getId())))
                throw new BadRequestException("KPI item IDs must be unique and belong to this working revision");
            k.setPlan(v.getPlan());k.setPlanId(v.getPlan().getId());k.setReviewPeriodId(v.getReviewPeriodId());k.setVersion(v);
            items.update(input,k);replacement.add(k);
        }
        v.getItems().removeIf(k->!replacement.contains(k));replacement.forEach(k->{if(!v.getItems().contains(k)) v.getItems().add(k);});
        // A saved meaningful edit satisfies a return; no-op saves and formatting do not.
        if(v.isRevisionRequired() && !KpiPlanReturnFeedback.compare(before,v.getItems().stream().map(items::toDto).toList()).isEmpty())
            v.setRevisionRequired(false);
        return save(v,actor);
    }
    @PreAuthorize("hasAnyAuthority('CAN_PREPARE_DEPARTMENT_KPI_REVISION','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS')")
    public KpiRevisionDto submit(Long id,KpiRevisionRequest request,UUID actor) {
        var v=writing(id,request,actor);editor(v,actor);editable(v);
        if(v.getLevel()==KpiLevel.COMPANY || (v.getLevel()==KpiLevel.INDIVIDUAL && v.getPreparationMode()!=KpiRevisionPreparationMode.HR_MD))
            throw new BadRequestException("This revision uses direct final confirmation");
        complete(v);v.setSubmittedAt(now());transition(v,"SUBMITTED",actor,
            v.getLevel()==KpiLevel.INDIVIDUAL?KpiVersionStatus.PENDING_APPROVAL:KpiVersionStatus.PENDING_MD_APPROVAL,null);
        var result=save(v,actor);
        if(v.getLevel()==KpiLevel.INDIVIDUAL) notifySuperior(v,currentSuperior(v.getPlan()),true);
        return result;
    }
    @PreAuthorize("hasAnyAuthority('CAN_APPROVE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto approve(Long id,KpiRevisionRequest request,UUID actor) {
        var v=writing(id,request,actor);
        if(v.getLevel()==KpiLevel.INDIVIDUAL) {
            superior(v,actor);requireState(v,KpiVersionStatus.PENDING_APPROVAL);
            if(v.getPreparationMode()!=KpiRevisionPreparationMode.HR_MD) deny();
            return finalise(v,actor,true);
        }
        permission(MD);requireState(v,KpiVersionStatus.PENDING_MD_APPROVAL);
        if(v.getLevel()!=KpiLevel.DEPARTMENT || actor.equals(v.getPreparedBy()) || actor.equals(v.getRequestedBy())) deny();
        complete(v);v.setApprovedBy(actor);v.setApprovedAt(now());
        transition(v,"MD_APPROVED",actor,KpiVersionStatus.PENDING_HOD_CONFIRMATION,null);return save(v,actor);
    }
    @PreAuthorize("hasAnyAuthority('CAN_APPROVE_DEPARTMENT_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto returnToHr(Long id,KpiRevisionRequest request,UUID actor) {
        var v=writing(id,request,actor);var reason=reason(request);
        if(v.getLevel()==KpiLevel.INDIVIDUAL) {
            superior(v,actor);requireState(v,KpiVersionStatus.PENDING_APPROVAL);
            if(v.getPreparationMode()!=KpiRevisionPreparationMode.HR_MD) deny();
        } else if(v.getLevel()!=KpiLevel.DEPARTMENT) deny();
        else if(v.getStatus()==KpiVersionStatus.PENDING_MD_APPROVAL) {
            permission(MD);if(actor.equals(v.getPreparedBy())||actor.equals(v.getRequestedBy())) deny();
        } else {requireState(v,KpiVersionStatus.PENDING_HOD_CONFIRMATION);requestingHod(v,actor);}
        v.setLatestReturnReason(reason);v.setLatestReturnedBy(actor);v.setLatestReturnedAt(now());
        v.setLatestReturnItems(v.getItems().stream().map(items::toDto).toList());v.setRevisionRequired(true);
        v.setApprovedBy(null);v.setApprovedAt(null);
        transition(v,"RETURNED",actor,KpiVersionStatus.RETURNED,reason);return save(v,actor);
    }
    @PreAuthorize("hasAnyAuthority('CAN_REVISE_COMPANY_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiRevisionDto confirm(Long id,KpiRevisionRequest request,UUID actor) {
        var v=writing(id,request,actor);
        if(v.getLevel()==KpiLevel.COMPANY) {editor(v,actor);requireState(v,KpiVersionStatus.DRAFT);}
        else if(v.getLevel()==KpiLevel.INDIVIDUAL) {
            superior(v,actor);editor(v,actor);requireState(v,KpiVersionStatus.DRAFT);
            if(v.getPreparationMode()!=KpiRevisionPreparationMode.SUPERIOR) deny();
        } else {requireState(v,KpiVersionStatus.PENDING_HOD_CONFIRMATION);requestingHod(v,actor);}
        return finalise(v,actor,v.getLevel()==KpiLevel.INDIVIDUAL);
    }
    private KpiRevisionDto finalise(KpiPlanVersion v,UUID actor,boolean superiorApproval) {
        complete(v);assignments.requirePublishedRoster(v.getPlan().getReviewPeriod());
        var plan=v.getPlan();var current=plan.getCurrentVersion();
        if(!Objects.equals(current.getId(),v.getBaseVersion().getId()))
            throw new KpiVersionConflictException("The effective KPI version changed; refresh before continuing");
        current.setStatus(KpiVersionStatus.SUPERSEDED);versions.saveAndFlush(current);
        var at=now();v.setConfirmedBy(actor);v.setConfirmedAt(at);v.setEffectiveAt(at);v.setUpdatedBy(actor);v.setUpdatedAt(at);
        if(superiorApproval) {v.setApprovedBy(actor);v.setApprovedAt(at);}
        transition(v,superiorApproval?"SUPERIOR_APPROVED":"CONFIRMED",actor,KpiVersionStatus.EFFECTIVE,null);versions.saveAndFlush(v);
        plan.setCurrentVersion(v);plan.setUpdatedAt(now());plan.setUpdatedBy(actor);plans.saveAndFlush(plan);
        assignments.cascade(plan);return details(v,actor,true);
    }
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS')")
    public KpiRevisionDto cancel(Long id,KpiRevisionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var v=version(id,true);exceptional(v);token(v,request);
        publishedWritable(v.getPlan().getReviewPeriod());
        if(!v.getStatus().unfinished()) throw new BadRequestException("Only an unfinished revision can be cancelled");
        if(!has(ACCESS)) {
            window(v.getPlan().getReviewPeriod());
            if(v.getLevel()==KpiLevel.COMPANY) {editor(v,actor);requireState(v,KpiVersionStatus.DRAFT);}
            else if(v.getLevel()==KpiLevel.INDIVIDUAL) {
                requireState(v,KpiVersionStatus.REQUESTED);permission(INDIVIDUAL);
                if(!actor.equals(v.getRequestedBy())) deny();
                requireRevisionRoute(v);
            } else {requireState(v,KpiVersionStatus.REQUESTED);requestingHod(v,actor);}
        }
        entityManager.lock(v,jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        var reason=reason(request);v.setCancelledFromState(v.getStatus());v.setCancelledBy(actor);
        v.setCancelledAt(now());v.setCancellationReason(reason);
        transition(v,"CANCELLED",actor,KpiVersionStatus.CANCELLED,reason);return save(v,actor);
    }
    private KpiRevisionDto save(KpiPlanVersion v,UUID actor) {v.setUpdatedBy(actor);v.setUpdatedAt(now());versions.saveAndFlush(v);return details(v,actor,true);}
    private KpiPlanVersion writing(Long id,KpiRevisionRequest request,UUID actor) {
        periods.lockConfiguration();active(actor);var v=version(id,true);exceptional(v);token(v,request);
        window(v.getPlan().getReviewPeriod());
        if(v.getLevel()==KpiLevel.INDIVIDUAL) requireRevisionRoute(v);
        // Item-only edits must invalidate stale requests even under the fixed UAT clock.
        entityManager.lock(v,jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);return v;
    }
    private void token(KpiPlanVersion v,KpiRevisionRequest request) {
        if(request==null || request.getExpectedLockVersion()==null || request.getExpectedLockVersion()!=v.getLockVersion())
            throw new KpiVersionConflictException("This revision has changed; refresh before continuing");
    }
    private void exceptional(KpiPlanVersion v) {
        if(v.getVersionNumber()==1)
            throw new BadRequestException("Original plans use their existing workflow");
        if(v.getLevel()==KpiLevel.INDIVIDUAL && (v.getPreparationMode()==null || v.getRevisionSuperiorId()==null))
            throw new BadRequestException("This Individual revision has no preparation mode or routed Superior");
    }
    private void complete(KpiPlanVersion v) {
        SubmissionRevisionGuard.requireRevisionComplete(v.isRevisionRequired());
        if(v.getLatestReturnItems()!=null && KpiPlanReturnFeedback.compare(v.getLatestReturnItems(),v.getItems().stream().map(items::toDto).toList()).isEmpty())
            throw new BadRequestException("Make at least one meaningful change since the latest return before resubmitting");
        validator.validate(v.getItems().stream().map(items::toDto).toList(),true);
        if(KpiVersionComparison.compare(v,items).isEmpty()) throw new BadRequestException("Make at least one meaningful change to the effective KPI plan");
    }
    private void editable(KpiPlanVersion v) {
        if(v.getStatus()!=KpiVersionStatus.DRAFT && v.getStatus()!=KpiVersionStatus.RETURNED)
            throw new BadRequestException("Only Draft or Returned revisions can be edited");
    }
    private void editor(KpiPlanVersion v,UUID actor) {
        if(v.getLevel()==KpiLevel.INDIVIDUAL) individualPreparer(v,actor);
        else permission(v.getLevel()==KpiLevel.COMPANY?COMPANY:HR);
        if(!actor.equals(v.getPreparedBy())) deny();
    }
    private Staff currentSuperior(KpiPlan plan) {
        var participant=plan.getOwnerParticipant();
        if(participant==null || participant.getStaff()==null) throw new BadRequestException("This plan has no enrolled employee");
        var owner=active(participant.getStaff().getId());var manager=owner.getManager();
        if(manager==null || manager.getId().equals(owner.getId()))
            throw new BadRequestException("Assign an active immediate Superior before revising this KPI plan");
        var superior=active(manager.getId());var role=superior.getRole();
        if(role==null || role.isDeleted() || !roleAuthorities.existsByRoleIdAndAuthorityName(role.getId(),AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI))
            throw new BadRequestException("The immediate Superior needs permission to review Individual KPI Plans");
        return superior;
    }
    private void requireRevisionRoute(KpiPlanVersion v) {
        if(!Objects.equals(currentSuperior(v.getPlan()).getId(),v.getRevisionSuperiorId()))
            throw new BadRequestException("The employee's immediate Superior has changed. Ask HR to cancel this revision; it cannot be reassigned automatically");
    }
    private boolean isCurrentSuperior(KpiPlan plan,UUID actor) {
        var owner=plan.getOwnerParticipant()==null?null:plan.getOwnerParticipant().getStaff();
        return owner!=null && !owner.getId().equals(actor) && owner.getManager()!=null && actor.equals(owner.getManager().getId());
    }
    private boolean revisionRouteReadable(KpiPlanVersion v,UUID actor) {
        return v.getLevel()!=KpiLevel.INDIVIDUAL || has(INDIVIDUAL)||has(ACCESS)
            || v.getVersionNumber()==1 || actor.equals(v.getRevisionSuperiorId());
    }
    private void superior(KpiPlanVersion v,UUID actor) {
        permission(SUPERIOR);requireRevisionRoute(v);if(!actor.equals(v.getRevisionSuperiorId())) deny();
    }
    private void individualPreparer(KpiPlanVersion v,UUID actor) {
        if(v.getPreparationMode()==KpiRevisionPreparationMode.SUPERIOR) superior(v,actor);
        else {permission(INDIVIDUAL);if(!actor.equals(v.getRequestedBy())) deny();}
    }
    private void requestingHod(KpiPlanVersion v,UUID actor) {permission(HOD);hod(v.getPlan(),actor);if(!actor.equals(v.getRequestedBy())) deny();}
    private void hod(KpiPlan plan,UUID actor) {if(!Objects.equals(department(actor),plan.getDepartment().getId())) deny();}
    private Long department(UUID actor) {
        var employee=active(actor);var role=employee.getRole();
        if(role==null || role.isDeleted() || !role.isPerformanceReviewEligible()) deny();
        var node=departments.resolve(role);return node==null?null:node.getId();
    }
    private boolean operationalRead(KpiPlan plan,UUID actor) {
        if(plan.getLevel()==KpiLevel.COMPANY) return has(COMPANY)||has(ACCESS);
        if(plan.getLevel()==KpiLevel.INDIVIDUAL) return has(INDIVIDUAL)||has(ACCESS)||
            (has(SUPERIOR)&&isCurrentSuperior(plan,actor));
        if(plan.getLevel()!=KpiLevel.DEPARTMENT) return false;
        if(has(HR)||has(MD)||has(ACCESS)) return true;
        try {return has(HOD) && Objects.equals(department(actor),plan.getDepartment().getId());}
        catch(BadRequestException|AccessDeniedException e) {return false;}
    }
    private boolean employeeRead(KpiPlan plan,UUID actor) {
        if(!has("ROLE_USER")) return false;
        if(plan.getLevel()==KpiLevel.INDIVIDUAL) return plan.getOwnerParticipant().getStaff().getId().equals(actor);
        if(plan.getLevel()==KpiLevel.COMPANY && has("CAN_MANAGE_COMPANY_KPI")) return true;
        return participants.findByReviewPeriodIdAndStaffId(plan.getReviewPeriod().getId(),actor)
            .map(p->employeeAssignments.findAllByParticipantId(p.getId()).stream()
                .anyMatch(a->Objects.equals(a.getKpi().getPlanId(),plan.getId()))).orElse(false);
    }
    private void read(KpiPlanVersion v,UUID actor) {
        if(!(operationalRead(v.getPlan(),actor)&&revisionRouteReadable(v,actor)) && !(effective(v)&&employeeRead(v.getPlan(),actor))) deny();
    }
    private boolean effective(KpiPlanVersion v) {return v.getStatus()==KpiVersionStatus.EFFECTIVE||v.getStatus()==KpiVersionStatus.SUPERSEDED;}
    private boolean confirmed(KpiPlan p) {return p.getStatus()==KpiPlanStatus.APPROVED||p.getStatus()==KpiPlanStatus.PUBLISHED;}
    private void publishedWritable(AnnualKpiReviewPeriod p) {
        if(p.getStatus()!=AnnualKpiReviewPeriodStatus.UPCOMING && p.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            throw new BadRequestException("KPI revisions require an Upcoming or Open Review Period; Closed periods are read-only");
    }
    private void window(AnnualKpiReviewPeriod p) {
        publishedWritable(p);
        if(!p.isKpiAmendmentsEnabled() || expired(p)) throw new BadRequestException("KPI amendments are disabled or have expired; ask HR to reopen access");
    }
    private boolean expired(AnnualKpiReviewPeriod p) {return p.getKpiAmendmentsCloseDate()!=null && LocalDate.now(clock).isAfter(p.getKpiAmendmentsCloseDate());}
    private KpiAmendmentAccessDto accessDto(AnnualKpiReviewPeriod p) {
        return new KpiAmendmentAccessDto(p.getId(),p.getName(),p.getStatus(),p.isKpiAmendmentsEnabled(),p.getKpiAmendmentsCloseDate(),
            p.isKpiAmendmentsEnabled()&&!expired(p)&&(p.getStatus()==AnnualKpiReviewPeriodStatus.UPCOMING||p.getStatus()==AnnualKpiReviewPeriodStatus.OPEN),
            expired(p),p.getKpiAmendmentsChangedAt(),p.getKpiAmendmentsChangedBy());
    }
    private KpiRevisionDto details(KpiPlanVersion v,UUID actor,boolean internal) {
        var dto=mapper.toDto(v);dto.setTotalWeightage(validator.validate(dto.getItems(),false));
        var changes=KpiVersionComparison.compare(v,items);dto.setChanges(changes);
        dto.setTargetChangedItemIds(changes.stream().filter(c->c.type().equals("EDITED")&&c.fields().stream().anyMatch(f->f.field().equals("target")))
            .map(KpiPlanChangeDto::itemId).toList());
        var blockers=new ArrayList<String>();var actions=new LinkedHashSet<String>();var p=v.getPlan().getReviewPeriod();
        if(v.getVersionNumber()>1 && v.getStatus().unfinished() && internal) {
            if(p.getStatus()==AnnualKpiReviewPeriodStatus.CLOSED) blockers.add("Closed Review Periods are read-only");
            else {
                if(has(ACCESS)) actions.add("CANCEL");
                if(p.isKpiAmendmentsEnabled()&&!expired(p)) {
                    if(v.getLevel()==KpiLevel.COMPANY && has(COMPANY)&&actor.equals(v.getPreparedBy())&&v.getStatus()==KpiVersionStatus.DRAFT)
                        actions.addAll(List.of("EDIT","CONFIRM","CANCEL"));
                    if(v.getLevel()==KpiLevel.DEPARTMENT) {
                        if(v.getStatus()==KpiVersionStatus.REQUESTED && has(HR)&&!actor.equals(v.getRequestedBy())) actions.add("PREPARE");
                        if((v.getStatus()==KpiVersionStatus.DRAFT||v.getStatus()==KpiVersionStatus.RETURNED)&&has(HR)&&actor.equals(v.getPreparedBy())) actions.addAll(List.of("EDIT","SUBMIT"));
                        if(v.getStatus()==KpiVersionStatus.PENDING_MD_APPROVAL&&has(MD)&&!actor.equals(v.getPreparedBy())&&!actor.equals(v.getRequestedBy())) actions.addAll(List.of("APPROVE","RETURN"));
                        boolean hod=false;try {hod=has(HOD)&&actor.equals(v.getRequestedBy())&&Objects.equals(department(actor),v.getPlan().getDepartment().getId());} catch(RuntimeException ignored) {}
                        if(hod&&v.getStatus()==KpiVersionStatus.REQUESTED) actions.add("CANCEL");
                        if(hod&&v.getStatus()==KpiVersionStatus.PENDING_HOD_CONFIRMATION) actions.addAll(List.of("CONFIRM","RETURN"));
                    }
                    if(v.getLevel()==KpiLevel.INDIVIDUAL) {
                        if(v.getStatus()==KpiVersionStatus.REQUESTED && has(INDIVIDUAL)&&actor.equals(v.getRequestedBy())) actions.add("CANCEL");
                        try {
                            requireRevisionRoute(v);
                            boolean superior=has(SUPERIOR)&&actor.equals(v.getRevisionSuperiorId());
                            boolean preparer=v.getPreparationMode()==KpiRevisionPreparationMode.SUPERIOR?superior:
                                has(INDIVIDUAL)&&actor.equals(v.getRequestedBy());
                            if(v.getStatus()==KpiVersionStatus.REQUESTED && preparer) actions.add("PREPARE");
                            if((v.getStatus()==KpiVersionStatus.DRAFT||v.getStatus()==KpiVersionStatus.RETURNED)&&preparer&&actor.equals(v.getPreparedBy())) {
                                actions.add("EDIT");actions.add(v.getPreparationMode()==KpiRevisionPreparationMode.SUPERIOR?"CONFIRM":"SUBMIT");
                            }
                            if(v.getStatus()==KpiVersionStatus.PENDING_APPROVAL&&superior) actions.addAll(List.of("APPROVE","RETURN"));
                        } catch(BadRequestException|AccessDeniedException e) {
                            blockers.add(e.getMessage());if(!has(ACCESS)) actions.remove("CANCEL");
                        }
                    }
                    try {complete(v);} catch(BadRequestException e) {blockers.add(e.getMessage());actions.removeAll(List.of("SUBMIT","APPROVE","CONFIRM"));}
                } else blockers.add("KPI amendments are disabled or have expired; ask HR to reopen access");
            }
        }
        dto.setBlockers(blockers);dto.setAllowedActions(actions);
        var names=new LinkedHashMap<UUID,String>();
        var actors=new LinkedHashSet<>(Arrays.asList(v.getCreatedBy(),v.getPreparedBy(),v.getRequestedBy(),v.getApprovedBy(),v.getConfirmedBy(),v.getCancelledBy(),v.getLatestReturnedBy(),v.getRevisionSuperiorId()));
        v.getDecisionHistory().forEach(decision->actors.add(decision.actor()));
        for(var id:actors)
            if(id!=null) names.put(id,staff.findById(id).map(Staff::getName).orElse(null));
        dto.setActorNames(names);
        if(!internal) {
            dto.setLatestReturnReason(null);dto.setLatestReturnedBy(null);dto.setLatestReturnedAt(null);dto.setDecisionHistory(List.of());
            dto.setCancellationReason(null);dto.setPreparationMode(null);dto.setRevisionSuperiorId(null);
            dto.setActorNames(Map.of());
        }
        return dto;
    }
    private void transition(KpiPlanVersion v,String action,UUID actor,KpiVersionStatus to,String reason) {
        event(v,action,actor,v.getStatus(),to,reason);v.setStatus(to);
    }
    private void event(KpiPlanVersion v,String action,UUID actor,KpiVersionStatus from,KpiVersionStatus to,String reason) {
        // JSON snapshots normalise offsets to UTC; match that before flush to avoid a second version increment.
        v.getDecisionHistory().add(new KpiRevisionDecisionDto(action,actor,now().withOffsetSameInstant(ZoneOffset.UTC),from,to,reason));
    }
    private String reason(KpiRevisionRequest r) {
        String reason=r==null?null:r.getReason();
        if(reason==null||reason.isBlank()||reason.strip().length()>10000) throw new BadRequestException("Provide a reason of no more than 10000 characters");
        return reason.strip();
    }
    private void requireState(KpiPlanVersion v,KpiVersionStatus state) {if(v.getStatus()!=state) throw new BadRequestException("This revision is not "+state);}
    private AnnualKpiReviewPeriod period(Long id) {return periods.findById(id).orElseThrow(()->new BadRequestException("Review Period not found"));}
    private KpiPlanVersion version(Long id,boolean lock) {return (lock?versions.lockById(id):versions.findById(id)).orElseThrow(()->new BadRequestException("KPI version not found"));}
    private Staff active(UUID actor) {
        var s=staff.findById(actor).orElseThrow(()->new AccessDeniedException("Active staff account required"));
        if(s.isDeleted()||s.getAccountStatus()!=StaffAccountStatus.ACTIVE) deny();return s;
    }
    private boolean has(String permission) {var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals(permission));}
    private void permission(String permission) {if(!has(permission)) deny();}
    private void deny() {throw new AccessDeniedException("This revision action is not available to you");}
    private OffsetDateTime now() {return OffsetDateTime.now(clock);}
    private void notifySuperior(KpiPlanVersion v,Staff superior,boolean awaitingApproval) {
        String to=superior.getEmail(),employee=v.getPlan().getOwnerParticipant().getStaffName(),period=v.getPlan().getReviewPeriod().getName();
        Runnable send=()->{try {email.sendIndividualKpiRevisionEmail(to,employee,period,awaitingApproval,Locale.ENGLISH);}
            catch(RuntimeException e) {log.warn("Could not dispatch Individual KPI revision notification",e);}};
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {send.run();}
            });
        else send.run();
    }
}
