package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.mapper.KpiAssistanceMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @Transactional
public class KpiPlanServiceImpl implements KpiPlanService {
    private final KpiPlanRepository plans;
    private final AnnualKpiReviewPeriodRepository periods;
    private final KpiPlanMapper mapper;
    private final KpiPlanValidator validator;
    private final Clock clock;
    private final KpiAssignmentService assignments;
    private final StaffRepository staff;
    private final ReviewPeriodParticipantRepository participants;
    private final OrgChartRepository departments;
    private final PerformanceDepartmentResolver departmentResolver;
    private final IndividualKpiAssistanceAuthorizationRepository assistance;
    private final KpiAssistanceMapper assistanceMapper;
    public KpiPlanServiceImpl(KpiPlanRepository plans,AnnualKpiReviewPeriodRepository periods,
            KpiPlanMapper mapper,KpiPlanValidator validator,@Qualifier("annualKpiReviewClock") Clock clock, KpiAssignmentService assignments,
            StaffRepository staff, ReviewPeriodParticipantRepository participants, OrgChartRepository departments,
            PerformanceDepartmentResolver departmentResolver, IndividualKpiAssistanceAuthorizationRepository assistance,
            KpiAssistanceMapper assistanceMapper) {
        this.plans=plans; this.periods=periods; this.mapper=mapper; this.validator=validator; this.clock=clock;
        this.assignments=assignments;
        this.staff=staff; this.participants=participants; this.departments=departments; this.departmentResolver=departmentResolver;
        this.assistance=assistance;this.assistanceMapper=assistanceMapper;
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public List<KpiPeriodContextDto> companyPeriods() {
        return periods.findAllByOrderByStartDateDescIdDesc().stream().map(mapper::toContext).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public List<KpiPlanDto> companyPlans() {
        return plans.findAllByLevelOrderByUpdatedAtDesc(KpiLevel.COMPANY).stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto companyPlan(Long id) { return details(requirePlan(id,KpiLevel.COMPANY,false)); }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto createCompany(KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();
        if(request==null || request.getReviewPeriodId()==null) throw new BadRequestException("Select an Annual KPI Review Period");
        AnnualKpiReviewPeriod period=periods.findById(request.getReviewPeriodId()).orElseThrow(()->new BadRequestException("Review period not found"));
        requireWritable(period);
        if(plans.findByReviewPeriodIdAndLevel(period.getId(),KpiLevel.COMPANY).isPresent())
            throw new BadRequestException("This review period already has a Company KPI plan; open the existing plan");
        if(request.getDepartmentId()!=null || request.getOwnerParticipantId()!=null) throw new BadRequestException("Company plans cannot have a Department or Employee owner");
        var plan=new KpiPlan(); plan.setReviewPeriod(period); plan.setLevel(KpiLevel.COMPANY);
        plan.setCreatedAt(OffsetDateTime.now(clock)); plan.setCreatedBy(actor);
        plan.setUpdatedAt(plan.getCreatedAt()); plan.setUpdatedBy(actor);
        validator.validate(request.getItems(),false);
        plans.saveAndFlush(plan);
        replaceItems(plan,request.getItems());
        plans.saveAndFlush(plan);
        return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto updateCompany(Long id,KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();
        var plan=requirePlan(id,KpiLevel.COMPANY,true); requireEditable(plan);
        if(request==null || !Objects.equals(plan.getReviewPeriod().getId(),request.getReviewPeriodId())
                || request.getDepartmentId()!=null || request.getOwnerParticipantId()!=null)
            throw new BadRequestException("The KPI plan scope cannot be changed");
        replaceItems(plan,request.getItems()); touch(plan,actor); plans.saveAndFlush(plan); return details(plan);
    }
    private KpiPlan requirePlan(Long id,KpiLevel level,boolean lock) {
        var plan=(lock?plans.lockById(id):plans.findById(id)).orElseThrow(()->new BadRequestException("KPI plan not found"));
        if(plan.getLevel()!=level) throw new BadRequestException("KPI plan does not match this workflow");
        return plan;
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public KpiPlanDto publishCompany(Long id,UUID actor) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.COMPANY,true);requireEditable(plan);
        assignments.requirePublishedRoster(plan.getReviewPeriod());
        validator.validate(plan.getItems().stream().map(mapper::toDto).toList(),true);
        plan.setStatus(KpiPlanStatus.PUBLISHED);plan.setPublishedAt(OffsetDateTime.now(clock));plan.setPublishedBy(actor);
        plan.setPublishedLate(late(plan));touch(plan,actor);plans.saveAndFlush(plan);
        assignments.cascade(plan);return details(plan);
    }
    private boolean late(KpiPlan plan) {return plan.getReviewPeriod().getKpiSetupDeadline()!=null && LocalDate.now(clock).isAfter(plan.getReviewPeriod().getKpiSetupDeadline());}
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')")
    public List<KpiPeriodContextDto> departmentPeriods(UUID actor) {
        requireDepartmentAccess(actor);
        return periods.findAllByOrderByStartDateDescIdDesc().stream().map(mapper::toContext).toList();
    }
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')")
    public List<KpiDepartmentOptionDto> departmentOptions(UUID actor) {
        requireDepartmentAccess(actor);
        var nodes=canReviewDepartments()?departments.findAllByOrgChartTypeD():List.of(hodDepartment(actor));
        return nodes.stream().map(d->new KpiDepartmentOptionDto(d.getId(),d.getName())).toList();
    }
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')")
    public List<KpiPlanDto> departmentPlans(UUID actor) {
        requireDepartmentAccess(actor);
        var found=canReviewDepartments()?plans.findAllByLevelOrderByUpdatedAtDesc(KpiLevel.DEPARTMENT)
                :plans.findAllByLevelAndDepartmentIdOrderByUpdatedAtDesc(KpiLevel.DEPARTMENT,hodDepartment(actor).getId());
        return found.stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_APPROVE_DEPARTMENT_KPI')")
    public List<KpiPlanDto> pendingDepartmentPlans(UUID actor) {
        requireActiveStaff(actor);
        return plans.findAllByLevelAndStatusOrderBySubmittedAtAscIdAsc(KpiLevel.DEPARTMENT,KpiPlanStatus.PENDING_APPROVAL)
                .stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')")
    public KpiPlanDto departmentPlan(Long id,UUID actor) {
        requireDepartmentAccess(actor);
        var plan=requirePlan(id,KpiLevel.DEPARTMENT,false);
        if(!canReviewDepartments()) requireHodScope(plan.getDepartment().getId(),actor);
        return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiPlanDto createDepartment(KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();
        if(request==null || request.getReviewPeriodId()==null || request.getDepartmentId()==null)
            throw new BadRequestException("Select an Annual KPI Review Period and Department");
        if(request.getOwnerParticipantId()!=null) throw new BadRequestException("Department plans cannot have an Employee owner");
        var department=requireHodScope(request.getDepartmentId(),actor);
        var period=periods.findById(request.getReviewPeriodId()).orElseThrow(()->new BadRequestException("Review period not found"));
        requireWritable(period);
        if(plans.findByReviewPeriodIdAndLevelAndDepartmentId(period.getId(),KpiLevel.DEPARTMENT,department.getId()).isPresent())
            throw new BadRequestException("This Department already has a KPI plan for this review period; open the existing plan");
        validator.validate(request.getItems(),false);
        var plan=new KpiPlan();plan.setReviewPeriod(period);plan.setLevel(KpiLevel.DEPARTMENT);plan.setDepartment(department);
        plan.setCreatedAt(OffsetDateTime.now(clock));plan.setCreatedBy(actor);touch(plan,actor);
        plans.saveAndFlush(plan);replaceItems(plan,request.getItems());plans.saveAndFlush(plan);
        return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiPlanDto updateDepartment(Long id,KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.DEPARTMENT,true);
        requireHodScope(plan.getDepartment().getId(),actor);requireEditable(plan);
        if(request==null || !Objects.equals(plan.getReviewPeriod().getId(),request.getReviewPeriodId())
                || !Objects.equals(plan.getDepartment().getId(),request.getDepartmentId()) || request.getOwnerParticipantId()!=null)
            throw new BadRequestException("The KPI plan scope cannot be changed");
        replaceItems(plan,request.getItems());touch(plan,actor);plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_MANAGE_DEPARTMENT_KPI')")
    public KpiPlanDto submitDepartment(Long id,UUID actor) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.DEPARTMENT,true);
        requireHodScope(plan.getDepartment().getId(),actor);requireEditable(plan);
        SubmissionRevisionGuard.requireRevisionComplete(plan.isRevisionRequired());
        validator.validate(plan.getItems().stream().map(mapper::toDto).toList(),true);
        plan.setStatus(KpiPlanStatus.PENDING_APPROVAL);
        plan.setSubmittedAt(OffsetDateTime.now(clock));plan.setSubmittedBy(actor);plan.setSubmittedLate(late(plan));
        // Resubmission starts a new pending decision; retain the return reason while the HOD edits.
        plan.setReviewedAt(null);plan.setReviewedBy(null);plan.setReviewedLate(null);plan.setReturnReason(null);
        touch(plan,actor);plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_APPROVE_DEPARTMENT_KPI')")
    public KpiPlanDto approveDepartment(Long id,UUID actor) {
        periods.lockConfiguration();requireActiveStaff(actor);
        var plan=requirePlan(id,KpiLevel.DEPARTMENT,true);requirePendingReview(plan);
        assignments.requirePublishedRoster(plan.getReviewPeriod());
        validator.validate(plan.getItems().stream().map(mapper::toDto).toList(),true);
        plan.setStatus(KpiPlanStatus.APPROVED);review(plan,actor);plan.setReturnReason(null);
        plans.saveAndFlush(plan);assignments.cascade(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_APPROVE_DEPARTMENT_KPI')")
    public KpiPlanDto returnDepartment(Long id,KpiPlanReturnRequest request,UUID actor) {
        periods.lockConfiguration();requireActiveStaff(actor);
        var plan=requirePlan(id,KpiLevel.DEPARTMENT,true);requirePendingReview(plan);
        if(request==null || request.getReason()==null || request.getReason().isBlank() || request.getReason().length()>10000)
            throw new BadRequestException("Provide a return reason (maximum 10000 characters) so the HOD knows what to revise");
        plan.setStatus(KpiPlanStatus.RETURNED);plan.setReturnReason(request.getReason().strip());review(plan,actor);
        plan.setRevisionRequired(true);
        plans.saveAndFlush(plan);return details(plan);
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPeriodContextDto> individualPeriods(UUID actor) {
        requireActiveStaff(actor);
        return participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(actor).stream()
                .map(participant->{
                    var context=mapper.toContext(participant.getReviewPeriod());
                    context.setKpiAllocation(mapper.toDto(participant.getEmployeeLevelConfiguration()));
                    return context;
                }).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPlanDto> myIndividualPlans(UUID actor) {
        requireActiveStaff(actor);
        return plans.findAllByLevelAndOwnerStaffId(KpiLevel.INDIVIDUAL,actor).stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ROLE_USER')")
    public List<KpiPlanDto> myAssignedPlans(Long reviewPeriodId,UUID actor) {
        requireActiveStaff(actor);
        var participant=ownerParticipant(reviewPeriodId,actor);
        return plans.findAssignedPlans(participant.getId(),reviewPeriodId).stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiPlanDto> pendingIndividualPlans(UUID superior) {
        requireActiveStaff(superior);
        return plans.findAllByLevelAndStatusAndSubmittedToSuperiorIdOrderBySubmittedAtAscIdAsc(
                KpiLevel.INDIVIDUAL,KpiPlanStatus.PENDING_APPROVAL,superior).stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiPlanDto> individualReviewPlans(UUID superior) {
        requireActiveStaff(superior);
        return plans.findIndividualReviewPlans(KpiLevel.INDIVIDUAL,
                List.of(KpiPlanStatus.PENDING_APPROVAL,KpiPlanStatus.APPROVED,KpiPlanStatus.RETURNED),superior)
                .stream().map(this::details).toList();
    }
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto individualPlan(Long id,UUID actor) {
        requireActiveStaff(actor);
        var plan=requirePlan(id,KpiLevel.INDIVIDUAL,false);
        if(!plan.getOwnerParticipant().getStaff().getId().equals(actor)) {
            if(!canReviewIndividuals()) throw new AccessDeniedException("You may view only your own or routed subordinate's Individual KPI plan");
            requireImmediateSuperior(plan,actor);
        }
        return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiPlanDto createIndividual(KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();requireActiveStaff(actor);
        if(request==null || request.getReviewPeriodId()==null || request.getDepartmentId()!=null)
            throw new BadRequestException("Select an Annual KPI Review Period for your Individual KPI plan");
        var participant=ownerParticipant(request.getReviewPeriodId(),actor);
        if(request.getOwnerParticipantId()!=null && !request.getOwnerParticipantId().equals(participant.getId()))
            throw new AccessDeniedException("You can create only your own Individual KPI plan");
        requireWritable(participant.getReviewPeriod());
        assignments.requirePublishedRoster(participant.getReviewPeriod());
        if(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,participant.getId()).isPresent())
            throw new BadRequestException("You already have an Individual KPI plan for this review period");
        validator.validate(request.getItems(),false);
        var plan=new KpiPlan();plan.setReviewPeriod(participant.getReviewPeriod());plan.setLevel(KpiLevel.INDIVIDUAL);
        plan.setOwnerParticipantId(participant.getId());plan.setOwnerParticipant(participant);
        plan.setCreatedAt(OffsetDateTime.now(clock));plan.setCreatedBy(actor);touch(plan,actor);
        plans.saveAndFlush(plan);replaceItems(plan,request.getItems());plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiPlanDto updateIndividual(Long id,KpiPlanRequest request,UUID actor) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.INDIVIDUAL,true);
        requireIndividualOwner(plan,actor);requireEditable(plan);
        requireUnassistedPlan(plan);
        if(request==null || !Objects.equals(plan.getReviewPeriod().getId(),request.getReviewPeriodId())
                || request.getDepartmentId()!=null || (request.getOwnerParticipantId()!=null
                && !request.getOwnerParticipantId().equals(plan.getOwnerParticipantId())))
            throw new BadRequestException("The KPI plan scope cannot be changed");
        replaceItems(plan,request.getItems());touch(plan,actor);plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('ROLE_USER')")
    public KpiPlanDto submitIndividual(Long id,UUID actor) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.INDIVIDUAL,true);
        var owner=requireIndividualOwner(plan,actor);requireEditable(plan);
        requireUnassistedPlan(plan);
        SubmissionRevisionGuard.requireRevisionComplete(plan.isRevisionRequired());
        validator.validate(plan.getItems().stream().map(mapper::toDto).toList(),true);
        var superior=owner.getManager();
        if(superior==null || superior.getId().equals(actor) || superior.isDeleted()
                || superior.getAccountStatus()!=StaffAccountStatus.ACTIVE)
            throw new BadRequestException("Assign an active immediate Superior before submitting this KPI plan");
        plan.setStatus(KpiPlanStatus.PENDING_APPROVAL);
        plan.setSubmittedAt(OffsetDateTime.now(clock));plan.setSubmittedBy(actor);plan.setSubmitter(owner);
        plan.setSubmittedToSuperiorId(superior.getId());plan.setSubmittedToSuperior(superior);
        plan.setSubmittedLate(late(plan));plan.setReviewedAt(null);plan.setReviewedBy(null);
        plan.setReviewedLate(null);plan.setReturnReason(null);touch(plan,actor);
        plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto approveIndividual(Long id,UUID superior) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.INDIVIDUAL,true);
        requireImmediateSuperior(plan,superior);requirePendingReview(plan);
        assignments.requirePublishedRoster(plan.getReviewPeriod());
        validator.validate(plan.getItems().stream().map(mapper::toDto).toList(),true);
        plan.setStatus(KpiPlanStatus.APPROVED);review(plan,superior);plan.setReturnReason(null);
        plans.saveAndFlush(plan);assignments.cascade(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto returnIndividual(Long id,KpiPlanReturnRequest request,UUID superior) {
        periods.lockConfiguration();var plan=requirePlan(id,KpiLevel.INDIVIDUAL,true);
        requireImmediateSuperior(plan,superior);requirePendingReview(plan);
        if(request==null || request.getReason()==null || request.getReason().isBlank() || request.getReason().length()>10000)
            throw new BadRequestException("Provide a return reason (maximum 10000 characters) so the employee knows what to revise");
        plan.setStatus(KpiPlanStatus.RETURNED);plan.setReturnReason(request.getReason().strip());review(plan,superior);
        plan.setRevisionRequired(true);
        plans.saveAndFlush(plan);return details(plan);
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public List<KpiAssistanceEmployeeDto> assistanceEmployees(UUID superior) {
        requireActiveStaff(superior);
        return participants.findCurrentSubordinateParticipants(superior).stream()
                .filter(p->!p.getStaff().getId().equals(superior))
                .filter(p->plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,p.getId()).isEmpty())
                .map(assistanceMapper::toDto).toList();
    }
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_REVIEW_INDIVIDUAL_KPI','CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE')")
    public List<KpiAssistanceDto> assistanceCases(UUID actor) {
        requireActiveStaff(actor);
        var cases=canAuthorizeAssistance()?assistance.findAllByOrderByRequestedAtDescIdDesc():assistance.findCurrentSuperiorCases(actor);
        return cases.stream().map(this::assistanceDetails).toList();
    }
    @Override @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_REVIEW_INDIVIDUAL_KPI','CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE')")
    public KpiAssistanceDto assistanceCase(Long id,UUID actor) {
        requireActiveStaff(actor);var authorization=requireAssistance(id,false);
        if(!canAuthorizeAssistance()) requireAssistanceSuperior(authorization,actor);
        return assistanceDetails(authorization);
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiAssistanceDto requestAssistance(KpiAssistanceRequest request,UUID superior) {
        periods.lockConfiguration();var requester=requireActiveStaff(superior);
        if(request==null || request.getOwnerParticipantId()==null) throw new BadRequestException("Select an enrolled subordinate");
        var participant=participants.findById(request.getOwnerParticipantId())
                .orElseThrow(()->new BadRequestException("Review period participant not found"));
        requireSubordinate(participant,superior);requireWritable(participant.getReviewPeriod());
        assignments.requirePublishedRoster(participant.getReviewPeriod());requireNoIndividualPlan(participant);
        if(assistance.existsByOwnerParticipantIdAndSuperiorId(participant.getId(),superior))
            throw new BadRequestException("An assistance request already exists for this employee and review period; open the existing request");
        var authorization=new IndividualKpiAssistanceAuthorization();authorization.setOwnerParticipant(participant);
        authorization.setSuperior(requester);authorization.setRequestedAt(OffsetDateTime.now(clock));
        assistance.saveAndFlush(authorization);return assistanceDetails(authorization);
    }
    @Override @PreAuthorize("hasAuthority('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE')")
    public KpiAssistanceDto authorizeAssistance(Long id,UUID hr) {
        periods.lockConfiguration();var reviewer=requireActiveStaff(hr);var authorization=requireAssistance(id,true);
        if(authorization.getStatus()!=KpiAssistanceStatus.REQUESTED)
            throw new BadRequestException("Only a requested assistance case can be authorised");
        requireAssistanceSuperior(authorization,authorization.getSuperior().getId());
        var participant=authorization.getOwnerParticipant();requireWritable(participant.getReviewPeriod());
        assignments.requirePublishedRoster(participant.getReviewPeriod());requireNoIndividualPlan(participant);
        authorization.setStatus(KpiAssistanceStatus.AUTHORIZED);authorization.setAuthorizedAt(OffsetDateTime.now(clock));
        authorization.setAuthorizedBy(reviewer);assistance.saveAndFlush(authorization);return assistanceDetails(authorization);
    }
    @Override @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto assistedIndividualPlan(Long authorizationId,UUID superior) {
        var authorization=requireAssistance(authorizationId,false);requireAssistanceSuperior(authorization,superior);
        return details(requireAssistedPlan(authorization,false));
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto createAssistedIndividual(Long authorizationId,AssistedIndividualKpiPlanRequest request,UUID superior) {
        periods.lockConfiguration();var authorization=requireAuthorizedAssistance(authorizationId,superior);
        var participant=authorization.getOwnerParticipant();requireNoIndividualPlan(participant);
        if(request==null) throw new BadRequestException("Provide the Individual KPI items");
        validator.validate(request.getItems(),false);
        var plan=new KpiPlan();plan.setReviewPeriod(participant.getReviewPeriod());plan.setLevel(KpiLevel.INDIVIDUAL);
        plan.setOwnerParticipantId(participant.getId());plan.setOwnerParticipant(participant);
        plan.setAssistanceAuthorizationId(authorization.getId());plan.setCreatedAt(OffsetDateTime.now(clock));
        plan.setCreatedBy(superior);touch(plan,superior);plans.saveAndFlush(plan);
        replaceItems(plan,request.getItems());plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto updateAssistedIndividual(Long authorizationId,AssistedIndividualKpiPlanRequest request,UUID superior) {
        periods.lockConfiguration();var authorization=requireAuthorizedAssistance(authorizationId,superior);
        var plan=requireAssistedPlan(authorization,true);requireEditable(plan);
        if(request==null) throw new BadRequestException("Provide the Individual KPI items");
        replaceItems(plan,request.getItems());touch(plan,superior);plans.saveAndFlush(plan);return details(plan);
    }
    @Override @PreAuthorize("hasAuthority('CAN_REVIEW_INDIVIDUAL_KPI')")
    public KpiPlanDto confirmAssistedIndividual(Long authorizationId,UUID superior) {
        periods.lockConfiguration();var authorization=requireAuthorizedAssistance(authorizationId,superior);
        var plan=requireAssistedPlan(authorization,true);requireEditable(plan);
        validator.validate(plan.getItems().stream().map(mapper::toDto).toList(),true);
        // Record the Superior's direct confirmation, not a second approval or employee submission.
        plan.setStatus(KpiPlanStatus.APPROVED);review(plan,superior);plans.saveAndFlush(plan);
        authorization.setStatus(KpiAssistanceStatus.CONSUMED);authorization.setConsumedAt(OffsetDateTime.now(clock));
        assistance.saveAndFlush(authorization);assignments.cascade(plan);return details(plan);
    }
    private IndividualKpiAssistanceAuthorization requireAssistance(Long id,boolean lock) {
        if(id==null) throw new BadRequestException("Select an assistance request");
        return (lock?assistance.lockById(id):assistance.findById(id))
                .orElseThrow(()->new BadRequestException("Individual KPI assistance request not found"));
    }
    private IndividualKpiAssistanceAuthorization requireAuthorizedAssistance(Long id,UUID superior) {
        var authorization=requireAssistance(id,true);requireAssistanceSuperior(authorization,superior);
        if(authorization.getStatus()!=KpiAssistanceStatus.AUTHORIZED)
            throw new BadRequestException("HR must authorise this assistance request before creating or confirming the plan; consumed authorisations cannot be reused");
        var period=authorization.getOwnerParticipant().getReviewPeriod();requireWritable(period);assignments.requirePublishedRoster(period);
        return authorization;
    }
    private void requireAssistanceSuperior(IndividualKpiAssistanceAuthorization authorization,UUID actor) {
        if(!authorization.getSuperior().getId().equals(actor)) throw new AccessDeniedException("This assistance case belongs to another Superior");
        requireSubordinate(authorization.getOwnerParticipant(),actor);
    }
    private void requireSubordinate(ReviewPeriodParticipant participant,UUID superior) {
        requireActiveStaff(superior);var employee=requireActiveStaff(participant.getStaff().getId());
        if(employee.getId().equals(superior) || employee.getManager()==null || !employee.getManager().getId().equals(superior))
            throw new AccessDeniedException("You may assist only your currently assigned subordinates");
    }
    private void requireNoIndividualPlan(ReviewPeriodParticipant participant) {
        if(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,participant.getId()).isPresent())
            throw new BadRequestException("This employee already has an Individual KPI plan; assistance cannot replace or take over it");
    }
    private KpiPlan requireAssistedPlan(IndividualKpiAssistanceAuthorization authorization,boolean lock) {
        var found=plans.findByAssistanceAuthorizationId(authorization.getId())
                .orElseThrow(()->new BadRequestException("Create the authorised Individual KPI plan first"));
        var plan=lock?requirePlan(found.getId(),KpiLevel.INDIVIDUAL,true):found;
        if(plan.getLevel()!=KpiLevel.INDIVIDUAL || !Objects.equals(plan.getOwnerParticipantId(),authorization.getOwnerParticipant().getId())
                || !Objects.equals(plan.getReviewPeriod().getId(),authorization.getOwnerParticipant().getReviewPeriod().getId())
                || !Objects.equals(plan.getCreatedBy(),authorization.getSuperior().getId()))
            throw new BadRequestException("The plan does not match this scoped assistance authorisation");
        return plan;
    }
    private KpiAssistanceDto assistanceDetails(IndividualKpiAssistanceAuthorization authorization) {
        var dto=assistanceMapper.toDto(authorization);
        plans.findByAssistanceAuthorizationId(authorization.getId()).ifPresent(p->dto.setPlanId(p.getId()));return dto;
    }
    private boolean canAuthorizeAssistance() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication!=null && authentication.getAuthorities().stream()
                .anyMatch(a->a.getAuthority().equals("CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE"));
    }
    private void requireUnassistedPlan(KpiPlan plan) {
        if(plan.getAssistanceAuthorizationId()!=null)
            throw new BadRequestException("This plan is prepared through authorised Superior assistance; use that workflow to complete it");
    }
    private ReviewPeriodParticipant ownerParticipant(Long periodId,UUID actor) {
        if(periodId==null) throw new BadRequestException("Select an Annual KPI Review Period");
        return participants.findByReviewPeriodIdAndStaffId(periodId,actor)
                .orElseThrow(()->new AccessDeniedException("You are not included in this Annual KPI Review Period"));
    }
    private Staff requireIndividualOwner(KpiPlan plan,UUID actor) {
        var owner=requireActiveStaff(actor);
        if(plan.getOwnerParticipant()==null || !plan.getOwnerParticipant().getStaff().getId().equals(actor))
            throw new AccessDeniedException("You can change only your own Individual KPI plan");
        return owner;
    }
    private void requireImmediateSuperior(KpiPlan plan,UUID actor) {
        requireActiveStaff(actor);
        var owner=plan.getOwnerParticipant().getStaff();
        if(!Objects.equals(plan.getSubmittedToSuperiorId(),actor) || owner.getManager()==null
                || !owner.getManager().getId().equals(actor))
            throw new AccessDeniedException("Only the employee's routed immediate Superior can review this KPI plan");
    }
    private boolean canReviewIndividuals() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication!=null && authentication.getAuthorities().stream()
                .anyMatch(a->a.getAuthority().equals("CAN_REVIEW_INDIVIDUAL_KPI"));
    }
    private void requirePendingReview(KpiPlan plan) {
        requireWritable(plan.getReviewPeriod());
        if(plan.getStatus()!=KpiPlanStatus.PENDING_APPROVAL) throw new BadRequestException("Only Pending Approval plans can be approved or returned");
    }
    private void review(KpiPlan plan,UUID actor) {
        plan.setReviewedAt(OffsetDateTime.now(clock));plan.setReviewedBy(actor);plan.setReviewedLate(late(plan));touch(plan,actor);
    }
    private void requireDepartmentAccess(UUID actor) {
        if(canReviewDepartments()) requireActiveStaff(actor);
        else requireBusinessStaff(actor);
    }
    private Staff requireActiveStaff(UUID actor) {
        var employee=staff.findById(actor).orElseThrow(()->new AccessDeniedException("Staff account not found"));
        if(employee.isDeleted() || employee.getAccountStatus()!=StaffAccountStatus.ACTIVE)
            throw new AccessDeniedException("An active staff account is required for this workflow");
        return employee;
    }
    private Staff requireBusinessStaff(UUID actor) {
        var employee=requireActiveStaff(actor);
        var role=employee.getRole();
        if(role==null || role.isDeleted() || !role.isPerformanceReviewEligible())
            throw new AccessDeniedException("An active business staff account is required for this workflow");
        return employee;
    }
    private OrgChart hodDepartment(UUID actor) {
        var department=departmentResolver.resolve(requireBusinessStaff(actor).getRole());
        if(department==null) throw new BadRequestException("Confirm the HOD's Department in the organisation structure");
        return department;
    }
    private OrgChart requireHodScope(Long departmentId,UUID actor) {
        var department=hodDepartment(actor);
        if(!Objects.equals(department.getId(),departmentId)) throw new AccessDeniedException("You can manage only your assigned Department's KPI plan");
        return department;
    }
    private boolean canReviewDepartments() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication!=null && authentication.getAuthorities().stream()
                .anyMatch(a->a.getAuthority().equals("CAN_APPROVE_DEPARTMENT_KPI"));
    }
    private void requireWritable(AnnualKpiReviewPeriod period) {
        if(period.getStatus()==AnnualKpiReviewPeriodStatus.CLOSED) throw new BadRequestException("Closed review periods are read-only");
    }
    private void requireEditable(KpiPlan plan) {
        requireWritable(plan.getReviewPeriod());
        if(plan.getStatus()!=KpiPlanStatus.DRAFT && plan.getStatus()!=KpiPlanStatus.RETURNED)
            throw new BadRequestException("Only Draft or Returned KPI plans can be edited");
    }
    private void replaceItems(KpiPlan plan,List<KpiItemDto> inputs) {
        validator.validate(inputs,false);
        var before=revisionContent(plan);
        Map<Long,Kpi> existing=new HashMap<>(); plan.getItems().forEach(i->existing.put(i.getId(),i));
        Set<Long> seen=new HashSet<>(); List<Kpi> replacement=new ArrayList<>();
        for(var input:inputs) {
            Kpi item;
            if(input.getId()!=null) {
                item=existing.get(input.getId());
                if(item==null || !seen.add(input.getId())) throw new BadRequestException("KPI item IDs must be unique and belong to this plan");
            } else { item=new Kpi(); item.setPlan(plan); item.setPlanId(plan.getId()); item.setReviewPeriodId(plan.getReviewPeriod().getId()); }
            mapper.update(input,item);
            if(item.getName()!=null) item.setName(item.getName().strip());
            replacement.add(item);
        }
        plan.getItems().removeIf(i->!replacement.contains(i));
        replacement.forEach(i->{if(!plan.getItems().contains(i)) plan.getItems().add(i);});
        plan.setRevisionRequired(SubmissionRevisionGuard.afterSave(plan.isRevisionRequired(),before,revisionContent(plan)));
    }
    private record ItemRevisionContent(String name,String description,String perspective,String kra,String target,
            String measurementUnit,java.math.BigDecimal weightage,Map<Integer,String> scoringDefinitions) {}
    private Map<ItemRevisionContent,Long> revisionContent(KpiPlan plan) {
        // Compare content as a multiset: order and generated item IDs are not revisions.
        var content=new HashMap<ItemRevisionContent,Long>();
        for(var item:plan.getItems()) {
            var scoring=new TreeMap<Integer,String>();
            item.getScoringDefinitions().forEach((point,definition)->{
                var text=SubmissionRevisionGuard.text(definition);
                if(text!=null) scoring.put(point,text);
            });
            var value=new ItemRevisionContent(SubmissionRevisionGuard.text(item.getName()),
                    SubmissionRevisionGuard.text(item.getDescription()),SubmissionRevisionGuard.text(item.getPerspective()),
                    SubmissionRevisionGuard.text(item.getKra()),SubmissionRevisionGuard.text(item.getTarget()),
                    SubmissionRevisionGuard.text(item.getMeasurementUnit()),SubmissionRevisionGuard.decimal(item.getWeightage()),scoring);
            content.merge(value,1L,Long::sum);
        }
        return content;
    }
    private void touch(KpiPlan plan,UUID actor) { plan.setUpdatedBy(actor); plan.setUpdatedAt(OffsetDateTime.now(clock)); }
    private KpiPlanDto details(KpiPlan plan) {
        var dto=mapper.toDto(plan); dto.setTotalWeightage(validator.validate(dto.getItems(),false));
        if(plan.getLevel()==KpiLevel.INDIVIDUAL && plan.getOwnerParticipant()!=null) {
            var participant=plan.getOwnerParticipant();
            dto.setDepartmentName(participant.getDepartmentName());
            dto.setDepartmentId(participant.getDepartment()==null?null:participant.getDepartment().getId());
        }
        dto.setOverdue(plan.getReviewPeriod().getKpiSetupDeadline()!=null && LocalDate.now(clock).isAfter(plan.getReviewPeriod().getKpiSetupDeadline())
                && plan.getStatus()!=KpiPlanStatus.PUBLISHED && plan.getStatus()!=KpiPlanStatus.APPROVED);
        return dto;
    }
}
