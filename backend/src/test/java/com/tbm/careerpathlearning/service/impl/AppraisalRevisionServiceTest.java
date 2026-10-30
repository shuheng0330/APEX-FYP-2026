package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppraisalRevisionServiceTest {
    AppraisalRecordRepository records=mock(AppraisalRecordRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    EvaluationCycleRepository cycles=mock(EvaluationCycleRepository.class);
    AppraisalRecordServiceImpl service=spy(new AppraisalRecordServiceImpl());
    UUID employeeId=UUID.randomUUID(),managerId=UUID.randomUUID(),hrId=UUID.randomUUID(),id=UUID.randomUUID();
    AppraisalRecord record;AppraisalRecordDto request;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"appraisalRecordRepository",records);
        ReflectionTestUtils.setField(service,"staffRepository",staff);
        ReflectionTestUtils.setField(service,"evaluationCycleRepository",cycles);
        var employee=new Staff();employee.setId(employeeId);employee.setName("Employee");
        var manager=new Staff();manager.setId(managerId);manager.setName("Manager");
        var hr=new Staff();hr.setId(hrId);
        when(staff.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(staff.findByIdAndIsDeletedFalse(managerId)).thenReturn(Optional.of(manager));
        when(staff.findByIdAndIsDeletedFalse(hrId)).thenReturn(Optional.of(hr));
        var cycle=new EvaluationCycle();cycle.setId(1L);cycle.setEndDate(LocalDate.of(2027,12,31));
        when(cycles.findById(1L)).thenReturn(Optional.of(cycle));
        record=new AppraisalRecord();record.setId(id);record.setStaff(employee);record.setManager(manager);
        record.setEvaluationCycle(cycle);record.setStatus(AppraisalStatus.DRAFT);record.setReviewPeriodYears(1);
        record.setDecisionType(AppraisalDecisionType.PROMOTION);record.setManagerComment("Recommend based on consistent performance.");
        record.setPromotionSystemCategory(AppraisalCategory.READY);record.setPromotionManagerCategory(AppraisalCategory.READY);
        when(records.lockById(id)).thenReturn(Optional.of(record));
        when(records.findByStaff_IdAndEvaluationCycle_Id(employeeId,1L)).thenReturn(Optional.of(record));
        when(records.save(any())).thenAnswer(call->call.getArgument(0));
        var readiness=new AppraisalReadinessDto();readiness.setReadinessScore(new BigDecimal("80"));readiness.setSystemCategory(AppraisalCategory.READY);
        doReturn(readiness).when(service).calculateReadinessScore(employeeId,1L,1);
        request=new AppraisalRecordDto();request.setStaffId(employeeId);request.setEvaluationCycleId(1L);request.setReviewPeriodYears(1);
        request.setDecisionType(AppraisalDecisionType.PROMOTION);request.setManagerComment(record.getManagerComment());
    }
    void returned() {
        service.submit(id,managerId);var reason=new HrAppraisalActionDto();reason.setHrReturnReason("Explain recommendation");
        assertTrue(service.returnForRevision(id,reason,hrId).isRevisionRequired());
    }
    @Test void normalDraftAndFirstSubmissionAreUnaffected() {
        assertFalse(service.createOrUpdateDraft(request,managerId).isRevisionRequired());
        assertEquals(AppraisalStatus.PENDING_REVIEW,service.submit(id,managerId).getStatus());
    }
    @Test void noOpDraftSaveCannotBypassReturnedValidation() {
        returned();assertThrows(BadRequestException.class,()->service.submit(id,managerId));
        request.setManagerComment(" "+request.getManagerComment()+" ");request.setRevisionRequired(false);
        var saved=service.createOrUpdateDraft(request,managerId);
        assertEquals(AppraisalStatus.DRAFT,saved.getStatus());assertTrue(saved.isRevisionRequired());
        assertThrows(BadRequestException.class,()->service.submit(id,managerId));
    }
    @Test void managerContentChangeAllowsResubmitAndAnotherReturnResetsRequirement() {
        returned();request.setManagerComment("Revised recommendation with specific achievements.");
        assertFalse(service.createOrUpdateDraft(request,managerId).isRevisionRequired());
        assertEquals(AppraisalStatus.PENDING_REVIEW,service.submit(id,managerId).getStatus());
        var reason=new HrAppraisalActionDto();reason.setHrReturnReason("Another clarification");
        service.returnForRevision(id,reason,hrId);assertTrue(record.isRevisionRequired());
        assertThrows(BadRequestException.class,()->service.submit(id,managerId));
    }
    @Test void recalculatedReadinessDoesNotCountButManagerOverrideDoes() {
        returned();var readiness=new AppraisalReadinessDto();readiness.setReadinessScore(new BigDecimal("90"));
        readiness.setSystemCategory(AppraisalCategory.READY);
        doReturn(readiness).when(service).calculateReadinessScore(employeeId,1L,1);
        assertTrue(service.createOrUpdateDraft(request,managerId).isRevisionRequired());
        request.setPromotionManagerCategory(AppraisalCategory.BORDERLINE);request.setPromotionManagerOverrideReason("Needs further experience");
        assertFalse(service.createOrUpdateDraft(request,managerId).isRevisionRequired());
    }
}
