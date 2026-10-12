package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KpiRevisionServiceTest {
    KpiPlanVersionRepository versions=mock(KpiPlanVersionRepository.class);
    KpiPlanRepository plans=mock(KpiPlanRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    EmployeeKpiAssignmentRepository employeeAssignments=mock(EmployeeKpiAssignmentRepository.class);
    PerformanceDepartmentResolver departments=mock(PerformanceDepartmentResolver.class);
    KpiAssignmentService assignments=mock(KpiAssignmentService.class);
    RoleAuthorityRepository roleAuthorities=mock(RoleAuthorityRepository.class);
    EmailService email=mock(EmailService.class);
    KpiPlanMapper items=Mappers.getMapper(KpiPlanMapper.class);
    KpiRevisionService service;
    UUID md=UUID.randomUUID(),hr=UUID.randomUUID(),hod=UUID.randomUUID(),other=UUID.randomUUID();
    AnnualKpiReviewPeriod period;
    KpiPlan plan;
    KpiPlanVersion base,working;
    OrgChart department;
    List<KpiPlanVersion> history=new ArrayList<>();
    long sequence=2,itemSequence=100;

    @BeforeEach void setup() {
        var mapper=Mappers.getMapper(KpiRevisionMapper.class);ReflectionTestUtils.setField(mapper,"kpiPlanMapper",items);
        service=new KpiRevisionService(versions,plans,periods,staff,participants,employeeAssignments,departments,items,mapper,
            new KpiPlanValidator(),assignments,roleAuthorities,email,Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"),ZoneOffset.UTC));
        ReflectionTestUtils.setField(service,"entityManager",mock(jakarta.persistence.EntityManager.class));
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setEndDate(LocalDate.of(2026,9,30));period.setKpiAmendmentsEnabled(true);
        period.setParticipantsSnapshottedAt(OffsetDateTime.now());
        department=new OrgChart();department.setId(10L);department.setName("Sales");
        for(var id:List.of(md,hr,hod,other)) {
            var s=new Staff();s.setId(id);s.setName(id.toString());s.setAccountStatus(StaffAccountStatus.ACTIVE);
            var role=new Role();role.setId(10L);role.setPerformanceReviewEligible(true);s.setRole(role);
            when(staff.findById(id)).thenReturn(Optional.of(s));when(departments.resolve(role)).thenReturn(department);
        }
        plan=new KpiPlan();plan.setId(1L);plan.setLevel(KpiLevel.COMPANY);plan.setStatus(KpiPlanStatus.PUBLISHED);plan.setReviewPeriod(period);
        base=new KpiPlanVersion();base.setId(1L);base.setPlan(plan);base.setReviewPeriodId(1L);base.setLevel(KpiLevel.COMPANY);
        base.setVersionNumber(1);base.setStatus(KpiVersionStatus.EFFECTIVE);base.setEffectiveAt(OffsetDateTime.now());plan.setCurrentVersion(base);
        var item=new Kpi();item.setId(1L);item.setPlan(plan);item.setPlanId(1L);item.setVersion(base);item.setReviewPeriodId(1L);
        items.update(KpiPlanValidatorTest.item("Sales","100"),item);base.getItems().add(item);history.add(base);
        when(plans.lockById(1L)).thenReturn(Optional.of(plan));when(plans.findById(1L)).thenReturn(Optional.of(plan));
        when(periods.findById(1L)).thenReturn(Optional.of(period));
        when(versions.findAllByPlanIdOrderByVersionNumberDesc(1L)).thenAnswer(i->new ArrayList<>(history));
        when(versions.saveAndFlush(any())).thenAnswer(i->{
            KpiPlanVersion v=i.getArgument(0);
            if(v.getId()==null) {v.setId(sequence++);history.add(v);}
            v.getItems().forEach(k->{if(k.getId()==null) k.setId(itemSequence++);});
            when(versions.lockById(v.getId())).thenReturn(Optional.of(v));when(versions.findById(v.getId())).thenReturn(Optional.of(v));
            v.setLockVersion(v.getLockVersion()+1);if(v!=base) working=v;return v;
        });
        when(roleAuthorities.existsByRoleIdAndAuthorityName(10L,AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI)).thenReturn(true);
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    void login(UUID id,String... permissions) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,
        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));}
    KpiRevisionRequest request() {var r=new KpiRevisionRequest();r.setReason("Update annual target");if(working!=null)r.setExpectedLockVersion(working.getLockVersion());return r;}
    void company() {login(md,KpiRevisionService.COMPANY);service.startCompany(1L,request(),md);}
    void department() {
        plan.setLevel(KpiLevel.DEPARTMENT);plan.setStatus(KpiPlanStatus.APPROVED);plan.setDepartment(department);base.setLevel(KpiLevel.DEPARTMENT);
        login(hod,KpiRevisionService.HOD);service.requestDepartment(1L,request(),hod);
    }
    void edit(String target) {var r=request();r.setItems(new ArrayList<>(working.getItems().stream().map(items::toDto).toList()));r.getItems().get(0).setTarget(target);service.update(working.getId(),r,working.getPreparedBy());}
    void preparedDepartment() {department();login(hr,KpiRevisionService.HR);service.prepare(working.getId(),request(),hr);edit("New target");}

    @Test void companyRevisionKeepsBaseUntilConfirmationAndPreservesRootApproval() {
        var originalPublished=OffsetDateTime.now();plan.setPublishedAt(originalPublished);company();
        assertSame(base,plan.getCurrentVersion());assertEquals(1L,working.getItems().get(0).getPredecessorId());
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),md));
        edit("New company target");assertEquals(1,service.get(working.getId(),md).getTargetChangedItemIds().size());
        var result=service.confirm(working.getId(),request(),md);
        assertEquals(KpiVersionStatus.EFFECTIVE,result.getStatus());assertSame(working,plan.getCurrentVersion());
        assertEquals(KpiVersionStatus.SUPERSEDED,base.getStatus());assertEquals(originalPublished,plan.getPublishedAt());verify(assignments).cascade(plan);
    }
    @Test void hrCannotEditCompanyEvenWithAmendmentAccess() {
        company();login(hr,KpiRevisionService.ACCESS,KpiRevisionService.HR);
        var r=request();r.setItems(List.of());assertThrows(AccessDeniedException.class,()->service.update(working.getId(),r,hr));
        assertThrows(AccessDeniedException.class,()->service.confirm(working.getId(),request(),hr));
    }
    @Test void departmentRequiresThreeDifferentPeopleAndRequestingHodFinalConfirmation() {
        department();login(hod,KpiRevisionService.HR);assertThrows(AccessDeniedException.class,()->service.prepare(working.getId(),request(),hod));
        login(hr,KpiRevisionService.HR);service.prepare(working.getId(),request(),hr);edit("New target");service.submit(working.getId(),request(),hr);
        login(hr,KpiRevisionService.MD);assertThrows(AccessDeniedException.class,()->service.approve(working.getId(),request(),hr));
        login(hod,KpiRevisionService.MD);assertThrows(AccessDeniedException.class,()->service.approve(working.getId(),request(),hod));
        login(md,KpiRevisionService.MD);service.approve(working.getId(),request(),md);assertSame(base,plan.getCurrentVersion());
        login(other,KpiRevisionService.HOD);assertThrows(AccessDeniedException.class,()->service.confirm(working.getId(),request(),other));
        login(hod,KpiRevisionService.HOD);assertEquals(KpiVersionStatus.EFFECTIVE,service.confirm(working.getId(),request(),hod).getStatus());
    }
    @Test void repeatedReturnRequiresReasonAndSavedChangesAndRepeatsMdApproval() {
        preparedDepartment();service.submit(working.getId(),request(),hr);login(md,KpiRevisionService.MD);
        var r=request();r.setReason(" ");assertThrows(BadRequestException.class,()->service.returnToHr(working.getId(),r,md));
        service.returnToHr(working.getId(),request(),md);login(hr,KpiRevisionService.HR);
        assertThrows(BadRequestException.class,()->service.submit(working.getId(),request(),hr));
        edit("New target");assertTrue(working.isRevisionRequired());edit("Revised again");service.submit(working.getId(),request(),hr);
        login(md,KpiRevisionService.MD);service.approve(working.getId(),request(),md);
        login(hod,KpiRevisionService.HOD);service.returnToHr(working.getId(),request(),hod);assertNull(working.getApprovedBy());
        assertTrue(working.getDecisionHistory().stream().anyMatch(d->d.action().equals("MD_APPROVED")));
        assertEquals(md.toString(),service.get(working.getId(),hod).getActorNames().get(md));
        login(hr,KpiRevisionService.HR);edit("Final target");service.submit(working.getId(),request(),hr);
        assertEquals(KpiVersionStatus.PENDING_MD_APPROVAL,working.getStatus());
    }
    @Test void cancellationAuditsContentsAndReleasesGuardWithoutReusingNumber() {
        company();edit("Proposed target");var cancelled=working;service.cancel(working.getId(),request(),md);
        assertEquals(KpiVersionStatus.CANCELLED,cancelled.getStatus());assertEquals(KpiVersionStatus.DRAFT,cancelled.getCancelledFromState());
        assertEquals("Proposed target",cancelled.getItems().get(0).getTarget());assertSame(base,plan.getCurrentVersion());
        service.startCompany(1L,request(),md);assertEquals(3,working.getVersionNumber());verify(assignments,never()).cascade(any());
    }
    @Test void disabledOrExpiredAccessAllowsOnlyHrCancellationAndNeverClosedWrites() {
        company();period.setKpiAmendmentsEnabled(false);
        assertThrows(BadRequestException.class,()->service.cancel(working.getId(),request(),md));
        login(hr,KpiRevisionService.ACCESS);assertEquals(KpiVersionStatus.CANCELLED,service.cancel(working.getId(),request(),hr).getStatus());
        period.setKpiAmendmentsEnabled(true);company();period.setKpiAmendmentsCloseDate(LocalDate.of(2026,10,10));
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),md));
        login(hr,KpiRevisionService.ACCESS);period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.cancel(working.getId(),request(),hr));
        assertThrows(BadRequestException.class,()->service.updateAccess(1L,new KpiAmendmentAccessRequest(true,null),hr));
    }
    @Test void inclusiveAmendmentDateAndUpcomingOrEndedOpenRemainWritable() {
        period.setKpiAmendmentsCloseDate(LocalDate.of(2026,10,11));period.setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);company();
        edit("Upcoming revision");assertEquals(KpiVersionStatus.EFFECTIVE,service.confirm(working.getId(),request(),md).getStatus());
    }
    @Test void unfinishedGuardStaleTokensForeignItemAndInvalidIndividualScopeDenied() {
        company();assertThrows(KpiVersionConflictException.class,()->service.startCompany(1L,request(),md));
        var stale=request();stale.setExpectedLockVersion(-1L);assertThrows(KpiVersionConflictException.class,()->service.cancel(working.getId(),stale,md));
        var foreign=request();var item=KpiPlanValidatorTest.item("Foreign","100");item.setId(999L);foreign.setItems(List.of(item));
        assertThrows(BadRequestException.class,()->service.update(working.getId(),foreign,md));
        working.setLevel(KpiLevel.INDIVIDUAL);assertThrows(BadRequestException.class,()->service.cancel(working.getId(),request(),md));
        assertTrue(service.queue(1L,KpiLevel.INDIVIDUAL,null,md).isEmpty());
    }
    @Test void formattingOrTargetReversionDoesNotCountAsMeaningfulRevision() {
        company();String target=base.getItems().get(0).getTarget();edit(" "+target+"  ");
        assertTrue(service.get(working.getId(),md).getTargetChangedItemIds().isEmpty());
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),md));
        edit("Changed");edit(target);assertTrue(service.get(working.getId(),md).getChanges().isEmpty());
    }
    @Test void wrongDepartmentAndInactiveRequesterCannotConfirm() {
        preparedDepartment();service.submit(working.getId(),request(),hr);login(md,KpiRevisionService.MD);service.approve(working.getId(),request(),md);
        login(hod,KpiRevisionService.HOD);var foreign=new OrgChart();foreign.setId(99L);
        when(departments.resolve(staff.findById(hod).orElseThrow().getRole())).thenReturn(foreign);
        assertThrows(AccessDeniedException.class,()->service.confirm(working.getId(),request(),hod));
        staff.findById(hod).orElseThrow().setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class,()->service.confirm(working.getId(),request(),hod));
    }
    @Test void employeeHistoryHidesUnfinishedProposalsAndInternalReturnComments() {
        company();base.setLatestReturnReason("Internal reviewer feedback");base.setLatestReturnedBy(md);base.setLatestReturnedAt(OffsetDateTime.now());
        var participant=new ReviewPeriodParticipant();participant.setId(7L);
        when(participants.findByReviewPeriodIdAndStaffId(1L,other)).thenReturn(Optional.of(participant));
        var assignment=new EmployeeKpiAssignment();assignment.setKpi(base.getItems().get(0));
        when(employeeAssignments.findAllByParticipantId(7L)).thenReturn(List.of(assignment));
        login(other,"ROLE_USER");var result=service.history(1L,other);assertEquals(1,result.size());
        assertNull(result.get(0).getLatestReturnReason());assertTrue(result.get(0).getDecisionHistory().isEmpty());
        assertTrue(result.get(0).getAllowedActions().isEmpty());assertTrue(result.get(0).getActorNames().isEmpty());
        assertThrows(AccessDeniedException.class,()->service.get(working.getId(),other));
        when(participants.findByReviewPeriodIdAndStaffId(1L,other)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,()->service.history(1L,other));
    }

    void individual(KpiRevisionPreparationMode mode) {
        individualPlan();login(hr,KpiRevisionService.INDIVIDUAL);
        var r=request();r.setPreparationMode(mode);service.requestIndividual(1L,r,hr);
    }
    void individualPlan() {
        plan.setLevel(KpiLevel.INDIVIDUAL);plan.setStatus(KpiPlanStatus.APPROVED);base.setLevel(KpiLevel.INDIVIDUAL);
        var owner=staff.findById(other).orElseThrow();owner.setManager(staff.findById(hod).orElseThrow());
        var participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(owner);participant.setStaffName("Employee");
        plan.setOwnerParticipant(participant);plan.setOwnerParticipantId(7L);
    }
    @Test void eligiblePlanMetadataUsesPeriodSnapshotWithoutChangingReviewerScope() {
        individualPlan();var participant=plan.getOwnerParticipant();
        participant.setRoleName("Recorded Engineer");participant.setDepartmentName("Recorded IT");participant.setDepartment(department);
        when(plans.findAllByLevelOrderByUpdatedAtDesc(KpiLevel.INDIVIDUAL)).thenReturn(List.of(plan));
        login(hr,KpiRevisionService.INDIVIDUAL);
        var result=service.eligiblePlans(1L,hr);assertEquals(1,result.size());
        assertEquals("Recorded Engineer",result.get(0).getRoleName());assertEquals("Recorded IT",result.get(0).getDepartmentName());
        assertEquals(10L,result.get(0).getDepartmentId());
        login(md,KpiRevisionService.SUPERIOR);assertTrue(service.eligiblePlans(1L,md).isEmpty());
        login(hod,KpiRevisionService.SUPERIOR);assertEquals(1,service.eligiblePlans(1L,hod).size());
    }
    void prepareIndividual(KpiRevisionPreparationMode mode) {
        individual(mode);UUID preparer=mode==KpiRevisionPreparationMode.SUPERIOR?hod:hr;
        login(preparer,mode==KpiRevisionPreparationMode.SUPERIOR?KpiRevisionService.SUPERIOR:KpiRevisionService.INDIVIDUAL);
        service.prepare(working.getId(),request(),preparer);edit("Revised Individual target");
    }
    @Test void superiorConfirmationIsTheSingleApprovalAndImmediatelyEffective() {
        prepareIndividual(KpiRevisionPreparationMode.SUPERIOR);
        assertSame(base,plan.getCurrentVersion());var result=service.confirm(working.getId(),request(),hod);
        assertEquals(KpiVersionStatus.EFFECTIVE,result.getStatus());assertEquals(hod,result.getApprovedBy());
        assertEquals(result.getApprovedAt(),result.getEffectiveAt());assertEquals(result.getApprovedAt(),result.getConfirmedAt());
        assertEquals(hod,result.getUpdatedBy());assertEquals(result.getApprovedAt(),result.getUpdatedAt());
        assertEquals(hod,result.getConfirmedBy());assertSame(working,plan.getCurrentVersion());assertEquals(2,result.getVersionNumber());
        assertEquals(KpiVersionStatus.SUPERSEDED,base.getStatus());assertEquals(KpiPlanStatus.APPROVED,plan.getStatus());
        assertTrue(result.getDecisionHistory().stream().anyMatch(d->d.action().equals("SUPERIOR_APPROVED")&&d.actor().equals(hod)));
        verify(assignments).cascade(plan);verify(email).sendIndividualKpiRevisionEmail(any(),eq("Employee"),eq("Review"),eq(false),eq(Locale.ENGLISH));
    }
    @Test void hrPreparedRequiresSuperiorApprovalAndPendingIsImmutable() {
        prepareIndividual(KpiRevisionPreparationMode.HR_MD);service.submit(working.getId(),request(),hr);
        assertSame(base,plan.getCurrentVersion());assertEquals(KpiVersionStatus.PENDING_APPROVAL,working.getStatus());
        assertThrows(BadRequestException.class,()->edit("Cannot edit pending"));
        assertThrows(AccessDeniedException.class,()->service.approve(working.getId(),request(),hr));
        login(hod,KpiRevisionService.SUPERIOR);
        assertEquals(Set.of("APPROVE","RETURN"),service.get(working.getId(),hod).getAllowedActions());
        var result=service.approve(working.getId(),request(),hod);assertEquals(hod,result.getApprovedBy());
        assertEquals(result.getEffectiveAt(),result.getApprovedAt());assertEquals(KpiVersionStatus.EFFECTIVE,result.getStatus());
        verify(email).sendIndividualKpiRevisionEmail(any(),eq("Employee"),eq("Review"),eq(true),eq(Locale.ENGLISH));
        verify(assignments).cascade(plan);
    }
    @Test void individualReturnRequiresReasonAndMeaningfulChangesBeforeResubmission() {
        prepareIndividual(KpiRevisionPreparationMode.HR_MD);service.submit(working.getId(),request(),hr);login(hod,KpiRevisionService.SUPERIOR);
        var empty=request();empty.setReason(" ");assertThrows(BadRequestException.class,()->service.returnToHr(working.getId(),empty,hod));
        service.returnToHr(working.getId(),request(),hod);assertEquals(KpiVersionStatus.RETURNED,working.getStatus());
        login(hr,KpiRevisionService.INDIVIDUAL);assertThrows(BadRequestException.class,()->service.submit(working.getId(),request(),hr));
        edit(" Revised Individual target ");assertTrue(working.isRevisionRequired());
        edit("Better target");service.submit(working.getId(),request(),hr);assertEquals(KpiVersionStatus.PENDING_APPROVAL,working.getStatus());
        assertNotNull(working.getLatestReturnReason());
    }
    @Test void superiorCannotInitiateWithoutExplicitHrMdAuthorityAndDualActorUsesSuperiorPath() {
        individualPlan();login(hod,KpiRevisionService.SUPERIOR);assertThrows(AccessDeniedException.class,()->service.requestIndividual(1L,request(),hod));
        login(hod,KpiRevisionService.INDIVIDUAL,KpiRevisionService.SUPERIOR);var r=request();r.setPreparationMode(KpiRevisionPreparationMode.HR_MD);
        service.requestIndividual(1L,r,hod);assertEquals(KpiRevisionPreparationMode.SUPERIOR,working.getPreparationMode());
        service.prepare(working.getId(),request(),hod);edit("Superior proposed");service.confirm(working.getId(),request(),hod);
        assertEquals(hod,working.getApprovedBy());
    }
    @Test void wrongSuperiorEmployeeAndDifferentHrPreparerAreDenied() {
        individual(KpiRevisionPreparationMode.HR_MD);login(md,KpiRevisionService.INDIVIDUAL);
        assertThrows(AccessDeniedException.class,()->service.prepare(working.getId(),request(),md));
        login(other,KpiRevisionService.SUPERIOR);assertThrows(AccessDeniedException.class,()->service.get(working.getId(),other));
        login(md,KpiRevisionService.SUPERIOR);assertThrows(AccessDeniedException.class,()->service.get(working.getId(),md));
        login(hr,KpiRevisionService.INDIVIDUAL);service.prepare(working.getId(),request(),hr);edit("New employee target");service.submit(working.getId(),request(),hr);
        login(md,KpiRevisionService.SUPERIOR);assertThrows(AccessDeniedException.class,()->service.approve(working.getId(),request(),md));
    }
    @Test void changedReportingLineFreezesWritesAndNewSuperiorCannotSeeOldProposal() {
        prepareIndividual(KpiRevisionPreparationMode.SUPERIOR);
        staff.findById(other).orElseThrow().setManager(staff.findById(md).orElseThrow());
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),hod));
        login(md,KpiRevisionService.SUPERIOR);assertThrows(AccessDeniedException.class,()->service.get(working.getId(),md));
        when(versions.revisions()).thenReturn(List.of(working));assertTrue(service.queue(1L,KpiLevel.INDIVIDUAL,null,md).isEmpty());
        login(hr,KpiRevisionService.ACCESS);assertEquals(Set.of("CANCEL"),service.get(working.getId(),hr).getAllowedActions());
        assertEquals(KpiVersionStatus.CANCELLED,service.cancel(working.getId(),request(),hr).getStatus());assertSame(base,plan.getCurrentVersion());
    }
    @Test void missingInactiveOrUnpermittedSuperiorBlocksCreation() {
        individualPlan();login(hr,KpiRevisionService.INDIVIDUAL);var owner=staff.findById(other).orElseThrow();
        owner.setManager(null);assertThrows(BadRequestException.class,()->service.requestIndividual(1L,request(),hr));
        owner.setManager(staff.findById(hod).orElseThrow());owner.getManager().setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class,()->service.requestIndividual(1L,request(),hr));
        owner.getManager().setAccountStatus(StaffAccountStatus.ACTIVE);
        when(roleAuthorities.existsByRoleIdAndAuthorityName(10L,AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI)).thenReturn(false);
        assertThrows(BadRequestException.class,()->service.requestIndividual(1L,request(),hr));verify(versions,never()).saveAndFlush(any());
    }
    @Test void revokedSuperiorPermissionFreezesHrEditsButDoesNotPreventHrCancellation() {
        prepareIndividual(KpiRevisionPreparationMode.HR_MD);
        when(roleAuthorities.existsByRoleIdAndAuthorityName(10L,AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI)).thenReturn(false);
        assertThrows(BadRequestException.class,()->edit("Not permitted"));login(hr,KpiRevisionService.ACCESS);
        service.cancel(working.getId(),request(),hr);assertEquals(KpiVersionStatus.CANCELLED,working.getStatus());
    }
    @Test void individualInitiatorCanWithdrawOnlyBeforePreparationAndNumbersAreNotReused() {
        individual(null);assertEquals(KpiRevisionPreparationMode.SUPERIOR,working.getPreparationMode());
        service.cancel(working.getId(),request(),hr);assertEquals(KpiVersionStatus.REQUESTED,working.getCancelledFromState());
        service.requestIndividual(1L,request(),hr);assertEquals(3,working.getVersionNumber());
        login(hod,KpiRevisionService.SUPERIOR);service.prepare(working.getId(),request(),hod);
        login(hr,KpiRevisionService.INDIVIDUAL);assertThrows(BadRequestException.class,()->service.cancel(working.getId(),request(),hr));
    }
    @Test void individualRequiresCompleteMeaningfulChangesAndGuardsWindowAndStaleApproval() {
        individual(null);login(hod,KpiRevisionService.SUPERIOR);service.prepare(working.getId(),request(),hod);
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),hod));
        var r=request();r.setItems(List.of());service.update(working.getId(),r,hod);
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),hod));
        r=request();r.setItems(List.of(KpiPlanValidatorTest.item("New item","60")));service.update(working.getId(),r,hod);
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),hod));
        edit("New target");period.setKpiAmendmentsEnabled(false);assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),hod));
        period.setKpiAmendmentsEnabled(true);period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.confirm(working.getId(),request(),hod));
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);var stale=request();stale.setExpectedLockVersion(-1L);
        assertThrows(KpiVersionConflictException.class,()->service.confirm(working.getId(),stale,hod));verify(assignments,never()).cascade(any());
    }
    @Test void employeeSeesOnlyEffectiveIndividualHistory() {
        prepareIndividual(KpiRevisionPreparationMode.HR_MD);base.setLatestReturnReason("Internal feedback");
        login(other,"ROLE_USER");var result=service.history(1L,other);assertEquals(1,result.size());assertNull(result.get(0).getLatestReturnReason());
        assertTrue(result.get(0).getDecisionHistory().isEmpty());assertThrows(AccessDeniedException.class,()->service.get(working.getId(),other));
    }
    @Test void notificationsWaitForCommit() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            individual(null);verifyNoInteractions(email);
            var callbacks=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();assertEquals(1,callbacks.size());
            callbacks.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(email).sendIndividualKpiRevisionEmail(any(),eq("Employee"),eq("Review"),eq(false),eq(Locale.ENGLISH));
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void notificationFailureDoesNotDiscardRevisionAndRollbackDoesNotSend() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            individual(null);org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach(s->s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(email);
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
        login(hr,KpiRevisionService.ACCESS);service.cancel(working.getId(),request(),hr);
        doThrow(new IllegalStateException("Mail unavailable")).when(email).sendIndividualKpiRevisionEmail(any(),any(),any(),anyBoolean(),any());
        login(hr,KpiRevisionService.INDIVIDUAL);assertDoesNotThrow(()->service.requestIndividual(1L,request(),hr));
        assertEquals(KpiVersionStatus.REQUESTED,working.getStatus());
    }
}
