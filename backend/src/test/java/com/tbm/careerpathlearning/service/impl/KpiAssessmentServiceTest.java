package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KpiAssessmentServiceTest {
    KpiAssessmentRepository assessments=mock(KpiAssessmentRepository.class);
    KpiAssessmentItemRepository items=mock(KpiAssessmentItemRepository.class);
    KpiAssessmentEvidenceRepository files=mock(KpiAssessmentEvidenceRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    ReviewCheckpointRepository checkpoints=mock(ReviewCheckpointRepository.class);
    EmployeeKpiAssignmentRepository assignments=mock(EmployeeKpiAssignmentRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    KpiAssessmentEvidenceStorage storage=mock(KpiAssessmentEvidenceStorage.class);
    EmailService email=mock(EmailService.class);
    UUID owner=UUID.randomUUID(),superiorId=UUID.randomUUID(),outsider=UUID.randomUUID();
    Staff employee,superior;
    AnnualKpiReviewPeriod period;
    ReviewPeriodParticipant participant;
    ReviewCheckpoint checkpoint;
    KpiAssessment assessment;
    List<EmployeeKpiAssignment> assigned=new ArrayList<>();
    KpiAssessmentServiceImpl service;

    void date(String day) {
        service=new KpiAssessmentServiceImpl(assessments,items,files,participants,checkpoints,assignments,periods,staff,
                Mappers.getMapper(KpiPlanMapper.class),Mappers.getMapper(KpiAssessmentMapper.class),storage,email,
                Clock.fixed(Instant.parse(day+"T12:00:00Z"),ZoneOffset.UTC));
    }
    @BeforeEach void setup() {
        date("2027-02-01");
        superior=new Staff();superior.setId(superiorId);superior.setAccountStatus(StaffAccountStatus.ACTIVE);superior.setEmail("superior@example.test");
        employee=new Staff();employee.setId(owner);employee.setAccountStatus(StaffAccountStatus.ACTIVE);employee.setManager(superior);
        var stranger=new Staff();stranger.setId(outsider);stranger.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(owner)).thenReturn(Optional.of(employee));when(staff.findById(superiorId)).thenReturn(Optional.of(superior));
        when(staff.findById(outsider)).thenReturn(Optional.of(stranger));
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("Annual Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        var allocation=new ReviewPeriodEmployeeLevelConfiguration();allocation.setCompanyKpiWeight(new BigDecimal("15"));
        allocation.setDepartmentKpiWeight(new BigDecimal("25"));allocation.setIndividualKpiWeight(new BigDecimal("60"));
        participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(employee);participant.setStaffName("Amir");
        participant.setReviewPeriod(period);participant.setReviewFrequency(ReviewFrequency.MONTHLY);participant.setEmployeeLevelConfiguration(allocation);
        checkpoint=new ReviewCheckpoint();checkpoint.setId(11L);checkpoint.setReviewPeriod(period);checkpoint.setReviewFrequency(ReviewFrequency.MONTHLY);
        checkpoint.setSequenceNumber(1);checkpoint.setStartDate(LocalDate.of(2027,1,1));checkpoint.setEndDate(LocalDate.of(2027,1,31));
        checkpoint.setSelfAssessmentDeadline(LocalDate.of(2027,2,5));checkpoint.setSuperiorAssessmentDeadline(LocalDate.of(2027,2,10));
        when(participants.findByReviewPeriodIdAndStaffId(1L,owner)).thenReturn(Optional.of(participant));
        when(checkpoints.findById(11L)).thenReturn(Optional.of(checkpoint));
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L,ReviewFrequency.MONTHLY)).thenReturn(List.of(checkpoint));
        when(assignments.findAllByParticipantId(7L)).thenAnswer(i->new ArrayList<>(assigned));
        when(assessments.saveAndFlush(any())).thenAnswer(i->{KpiAssessment a=i.getArgument(0);a.setId(20L);assessment=a;
            when(assessments.findById(20L)).thenReturn(Optional.of(a));when(assessments.lockById(20L)).thenReturn(Optional.of(a));return a;});
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    KpiAssessmentRequest request() {var r=new KpiAssessmentRequest();r.setCheckpointId(11L);return r;}
    KpiAssessmentRequest.Answer answer(long id,Integer point) {var a=new KpiAssessmentRequest.Answer();a.setAssignmentId(id);a.setSelfPoint(point);a.setSelfComment("Evidence reviewed");return a;}
    void assign(KpiLevel level,long id) {
        var plan=new KpiPlan();plan.setId(id);plan.setReviewPeriod(period);plan.setLevel(level);
        plan.setStatus(level==KpiLevel.COMPANY?KpiPlanStatus.PUBLISHED:KpiPlanStatus.APPROVED);
        var kpi=new Kpi();kpi.setId(id);kpi.setName(level+" KPI");kpi.setPlan(plan);kpi.setPlanId(id);
        kpi.setWeightage(new BigDecimal("100"));plan.getItems().add(kpi);
        var a=new EmployeeKpiAssignment();a.setId(id);a.setKpi(kpi);a.setKpiId(id);a.setParticipant(participant);
        a.setParticipantId(7L);a.setReviewPeriodId(1L);assigned.add(a);
    }
    void complete() {assign(KpiLevel.COMPANY,1);assign(KpiLevel.DEPARTMENT,2);assign(KpiLevel.INDIVIDUAL,3);
        var r=request();r.setItems(List.of(answer(1,1),answer(2,2),answer(3,5)));service.create(r,owner);}
    void reviewer(UUID id,String permission) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority(permission))));}

    @Test void getNeverCreatesRecordAndExplainsMissingAssignments() {
        var dto=service.mine(11L,owner);assertNull(dto.getId());assertNull(dto.getCreatedAt());
        assertTrue(dto.isCanSaveDraft());assertFalse(dto.isCanSubmit());assertEquals(3,dto.getMissingLevels().size());
        assertTrue(dto.getItems().isEmpty());verify(assessments,never()).saveAndFlush(any());
    }
    @Test void emptyDraftSavesEvenWithoutAssignmentsOrSuperior() {
        employee.setManager(null);var dto=service.create(request(),owner);
        assertEquals(KpiAssessmentStatus.DRAFT,dto.getStatus());assertTrue(dto.getItems().isEmpty());
        assertThrows(BadRequestException.class,()->service.submit(20L,owner));verifyNoInteractions(email);
    }
    @Test void openingIsTheDayAfterEndDateForAllFrequencies() {
        for(var frequency:ReviewFrequency.values()) {
            participant.setReviewFrequency(frequency);checkpoint.setReviewFrequency(frequency);
            var end=switch(frequency) {
                case MONTHLY->LocalDate.of(2027,1,31);
                case QUARTERLY->LocalDate.of(2027,3,31);
                case ANNUALLY->LocalDate.of(2027,12,31);
            };
            checkpoint.setEndDate(end);checkpoint.setSelfAssessmentDeadline(end.plusDays(5));
            checkpoint.setSuperiorAssessmentDeadline(end.plusDays(10));
            date(end.toString());assertFalse(service.mine(11L,owner).getCheckpoint().isAvailable());
            assertThrows(BadRequestException.class,()->service.create(request(),owner));
            date(end.plusDays(1).toString());assertTrue(service.mine(11L,owner).getCheckpoint().isAvailable());
        }
    }
    @Test void deadlineDayIsOnTimeAndFollowingDayIsLate() {
        complete();date("2027-02-05");var submitted=service.submit(20L,owner);assertFalse(submitted.getSubmittedLate());
        assertEquals(KpiAssessmentStatus.PENDING_REVIEW,submitted.getStatus());assertEquals(superiorId,submitted.getSubmittedToSuperiorId());
        assertNull(submitted.getCheckpointScore());assessment.setStatus(KpiAssessmentStatus.DRAFT);
        date("2027-02-06");assertTrue(service.submit(20L,owner).getSubmittedLate());
    }
    @Test void finalCheckpointCanBeSubmittedAfterAnnualEndDate() {
        checkpoint.setReviewFrequency(ReviewFrequency.ANNUALLY);participant.setReviewFrequency(ReviewFrequency.ANNUALLY);
        checkpoint.setEndDate(LocalDate.of(2027,12,31));period.setEndDate(LocalDate.of(2027,12,31));
        checkpoint.setSelfAssessmentDeadline(LocalDate.of(2028,1,5));date("2028-01-01");complete();
        assertEquals(KpiAssessmentStatus.PENDING_REVIEW,service.submit(20L,owner).getStatus());
    }
    @Test void draftAnswersSurviveLateAssignmentAndSubmissionRequiresNewPoint() {
        assign(KpiLevel.COMPANY,1);var r=request();r.setItems(List.of(answer(1,4)));service.create(r,owner);
        assign(KpiLevel.DEPARTMENT,2);assign(KpiLevel.INDIVIDUAL,3);
        var projected=service.get(20L,owner);assertEquals(3,projected.getItems().size());assertEquals(1,assessment.getItems().size());
        assertEquals(4,projected.getItems().get(0).getSelfPoint());
        assertThrows(BadRequestException.class,()->service.submit(20L,owner));
        r.setItems(List.of(answer(2,3),answer(3,5)));service.update(20L,r,owner);
        assertEquals(4,assessment.getItems().get(0).getSelfPoint());assertTrue(service.get(20L,owner).isCanSubmit());
    }
    @Test void missingRequiredLevelHasSpecificReason() {
        assign(KpiLevel.COMPANY,1);var r=request();r.setItems(List.of(answer(1,5)));service.create(r,owner);
        var failure=assertThrows(BadRequestException.class,()->service.submit(20L,owner));assertTrue(failure.getMessage().contains("Department KPIs"));
    }
    @Test void zeroAllocatedLevelDoesNotRequireAPlan() {
        participant.getEmployeeLevelConfiguration().setDepartmentKpiWeight(BigDecimal.ZERO);
        assign(KpiLevel.COMPANY,1);assign(KpiLevel.INDIVIDUAL,3);var r=request();r.setItems(List.of(answer(1,4),answer(3,4)));
        service.create(r,owner);assertTrue(service.get(20L,owner).isCanSubmit());
    }
    @Test void incompleteAssignmentOfConfirmedPlanStillBlocksSubmission() {
        complete();var extra=new Kpi();extra.setId(100L);assigned.get(0).getKpi().getPlan().getItems().add(extra);
        assertTrue(service.get(20L,owner).getMissingLevels().contains(KpiLevel.COMPANY));
        assertThrows(BadRequestException.class,()->service.submit(20L,owner));
    }
    @Test void draftOrPendingPlansAreNotAssessmentItems() {
        assign(KpiLevel.DEPARTMENT,1);assigned.get(0).getKpi().getPlan().setStatus(KpiPlanStatus.PENDING_APPROVAL);
        assertTrue(service.mine(11L,owner).getItems().isEmpty());
    }
    @Test void invalidDuplicateAndForeignAnswersAreRejected() {
        assign(KpiLevel.COMPANY,1);var r=request();r.setItems(List.of(answer(1,0)));assertThrows(BadRequestException.class,()->service.create(r,owner));
        r.setItems(List.of(answer(1,6)));assertThrows(BadRequestException.class,()->service.create(r,owner));
        r.setItems(List.of(answer(1,3),answer(1,4)));assertThrows(BadRequestException.class,()->service.create(r,owner));
        r.setItems(List.of(answer(99,3)));assertThrows(AccessDeniedException.class,()->service.create(r,owner));
    }
    @Test void ownershipFrequencyAndDuplicateCheckpointAreProtected() {
        assertThrows(AccessDeniedException.class,()->service.create(request(),outsider));
        participant.setReviewFrequency(ReviewFrequency.QUARTERLY);assertThrows(AccessDeniedException.class,()->service.mine(11L,owner));
        participant.setReviewFrequency(ReviewFrequency.MONTHLY);complete();
        assertThrows(AccessDeniedException.class,()->service.update(20L,request(),outsider));
        when(assessments.findByParticipantIdAndCheckpointId(7L,11L)).thenReturn(Optional.of(assessment));
        assertThrows(BadRequestException.class,()->service.create(request(),owner));
    }
    @Test void pendingAndClosedAssessmentsAreReadOnlyAndFrozen() {
        complete();service.submit(20L,owner);assign(KpiLevel.COMPANY,9);assertEquals(3,service.get(20L,owner).getItems().size());
        assertThrows(BadRequestException.class,()->service.update(20L,request(),owner));
        assertThrows(BadRequestException.class,()->service.submit(20L,owner));
        assessment.setStatus(KpiAssessmentStatus.DRAFT);period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.update(20L,request(),owner));
    }
    @Test void missingInactiveDeletedOrSelfSuperiorBlocksOnlySubmission() {
        complete();for(int i=0;i<4;i++) {
            employee.setManager(i==0?null:i==3?employee:superior);superior.setDeleted(i==1);
            superior.setAccountStatus(i==2?StaffAccountStatus.INACTIVE:StaffAccountStatus.ACTIVE);
            assertThrows(BadRequestException.class,()->service.submit(20L,owner));
            assertDoesNotThrow(()->service.update(20L,request(),owner));
        }
    }
    @Test void reviewerNeedsPermissionCurrentRelationshipAndSavedRoute() {
        complete();service.submit(20L,owner);reviewer(superiorId,"CAN_REVIEW_INDIVIDUAL_KPI");
        assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        reviewer(superiorId,"CAN_REVIEW_KPI_ASSESSMENT");assertDoesNotThrow(()->service.get(20L,superiorId));
        employee.setManager(null);assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        assertDoesNotThrow(()->service.get(20L,owner));
    }
    @Test void supervisorCannotSeeEmployeeDraftOrUnfinishedSuperiorPoints() {
        complete();reviewer(superiorId,"CAN_REVIEW_KPI_ASSESSMENT");
        assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        service.submit(20L,owner);assessment.getItems().get(0).setSuperiorPoint(5);
        assertNull(service.get(20L,owner).getItems().get(0).getSuperiorPoint());
    }
    @Test void notificationWaitsForCommitAndSubmissionFailureSendsNothing() {
        complete();TransactionSynchronizationManager.initSynchronization();service.submit(20L,owner);
        verifyNoInteractions(email);TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(email).sendKpiSelfAssessmentSubmittedEmail(eq("superior@example.test"),eq("Amir"),eq("Annual Review"),eq("2027-01-31"),any());
    }
    @Test void evidenceMutationRequiresOwnerDraftAndFailedSaveCleansStorage() {
        complete();var item=assessment.getItems().get(0);item.setId(30L);when(items.findById(30L)).thenReturn(Optional.of(item));
        var upload=mock(org.springframework.web.multipart.MultipartFile.class);
        assertThrows(AccessDeniedException.class,()->service.upload(30L,upload,outsider));verifyNoInteractions(storage);
        var stored=new KpiAssessmentEvidenceStorage.Stored(UUID.randomUUID().toString(),"proof.pdf","application/pdf",20);
        when(storage.store(upload)).thenReturn(stored);when(files.saveAndFlush(any())).thenThrow(new IllegalStateException("DB failed"));
        assertThrows(IllegalStateException.class,()->service.upload(30L,upload,owner));verify(storage).delete(stored.key());
        assessment.setStatus(KpiAssessmentStatus.PENDING_REVIEW);assertThrows(BadRequestException.class,()->service.upload(30L,upload,owner));
    }
    @Test void evidenceDownloadAndDeletionAreScopedAndDeletionWaitsForCommit() {
        complete();var item=assessment.getItems().get(0);item.setId(30L);
        var file=new KpiAssessmentEvidence();file.setId(40L);file.setItem(item);file.setStorageKey(UUID.randomUUID().toString());
        when(files.findById(40L)).thenReturn(Optional.of(file));
        assertThrows(AccessDeniedException.class,()->service.download(40L,outsider));
        TransactionSynchronizationManager.initSynchronization();service.deleteEvidence(40L,owner);
        verify(storage,never()).delete(any());TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete(file.getStorageKey());
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);assertThrows(BadRequestException.class,()->service.deleteEvidence(40L,owner));
    }
    @Test void evidenceUploadIsCleanedAfterTransactionRollback() {
        complete();var item=assessment.getItems().get(0);item.setId(30L);when(items.findById(30L)).thenReturn(Optional.of(item));
        var upload=mock(org.springframework.web.multipart.MultipartFile.class);
        var stored=new KpiAssessmentEvidenceStorage.Stored(UUID.randomUUID().toString(),"proof.pdf","application/pdf",20);
        when(storage.store(upload)).thenReturn(stored);TransactionSynchronizationManager.initSynchronization();
        service.upload(30L,upload,owner);verify(storage,never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete(stored.key());
    }
    @Test void inactiveEmployeeCannotSaveReadOrSubmit() {
        complete();employee.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class,()->service.get(20L,owner));
        assertThrows(AccessDeniedException.class,()->service.update(20L,request(),owner));
        assertThrows(AccessDeniedException.class,()->service.submit(20L,owner));
    }
}
