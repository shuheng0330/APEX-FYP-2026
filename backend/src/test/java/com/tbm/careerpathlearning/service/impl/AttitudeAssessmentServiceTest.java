package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.EmailService;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttitudeAssessmentServiceTest {
    AttitudeAssessmentRepository assessments=mock(AttitudeAssessmentRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    EmailService email=mock(EmailService.class);
    UUID owner=UUID.randomUUID(),superiorId=UUID.randomUUID(),outsider=UUID.randomUUID();
    Staff employee,superior;
    ReviewPeriodParticipant participant;
    AnnualKpiReviewPeriod period;
    AttitudeConfiguration configuration;
    AttitudeAssessment assessment;
    AttitudeAssessmentServiceImpl service;

    void date(String day) {
        service=new AttitudeAssessmentServiceImpl(assessments,participants,periods,staff,
                Mappers.getMapper(AttitudeAssessmentMapper.class),Mappers.getMapper(AttitudeConfigurationMapper.class),
                Mappers.getMapper(KpiPlanMapper.class),email,Clock.fixed(Instant.parse(day+"T12:00:00Z"),ZoneOffset.UTC));
    }
    @BeforeEach void setup() {
        date("2027-01-01");
        superior=new Staff();superior.setId(superiorId);superior.setAccountStatus(StaffAccountStatus.ACTIVE);superior.setEmail("superior@example.test");
        employee=new Staff();employee.setId(owner);employee.setAccountStatus(StaffAccountStatus.ACTIVE);employee.setManager(superior);
        var stranger=new Staff();stranger.setId(outsider);stranger.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(owner)).thenReturn(Optional.of(employee));when(staff.findById(outsider)).thenReturn(Optional.of(stranger));
        when(staff.findById(superiorId)).thenReturn(Optional.of(superior));
        var role=new Role();role.setId(3L);role.setName("Recorded role");
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("Annual Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setStartDate(LocalDate.of(2027,1,1));period.setEndDate(LocalDate.of(2027,12,31));
        period.setAttitudeSelfAssessmentDeadline(LocalDate.of(2027,12,5));period.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(2027,12,15));
        participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(employee);participant.setStaffName("Amir");
        participant.setRole(role);participant.setRoleName("Recorded role");participant.setReviewPeriod(period);participant.setReviewFrequency(ReviewFrequency.MONTHLY);
        configuration=new AttitudeConfiguration();configuration.setId(10L);configuration.setName("Bound edition");configuration.setStatus(AttitudeConfigurationStatus.PUBLISHED);
        period.setAttitudeConfiguration(configuration);
        var mapping=new AttitudeRoleFormatMapping();mapping.setRole(role);mapping.setConfiguration(configuration);mapping.setEvaluationFormat(AttitudeEvaluationFormat.SALES);
        configuration.getRoleMappings().add(mapping);
        criterion(11L,AttitudeCriterionType.SHARED_CORE_VALUE,null,true);
        criterion(12L,AttitudeCriterionType.FORMAT_SPECIFIC,AttitudeEvaluationFormat.SALES,true);
        criterion(13L,AttitudeCriterionType.FORMAT_SPECIFIC,AttitudeEvaluationFormat.MANAGER,true);
        criterion(14L,AttitudeCriterionType.SHARED_CORE_VALUE,null,false);
        for(int point=1;point<=5;point++) {var rating=new AttitudeRatingDefinition();rating.setConfiguration(configuration);
            rating.setPoint(point);rating.setLabel("Rating "+point);rating.setDescription("Definition "+point);configuration.getRatingDefinitions().add(rating);}
        when(participants.findByReviewPeriodIdAndStaffId(1L,owner)).thenReturn(Optional.of(participant));
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(owner)).thenReturn(List.of(participant));
        when(assessments.saveAndFlush(any())).thenAnswer(invocation->{assessment=invocation.getArgument(0);assessment.setId(20L);
            when(assessments.findById(20L)).thenReturn(Optional.of(assessment));when(assessments.lockById(20L)).thenReturn(Optional.of(assessment));
            when(assessments.findByParticipantId(7L)).thenReturn(Optional.of(assessment));return assessment;});
    }
    void criterion(Long id,AttitudeCriterionType type,AttitudeEvaluationFormat format,boolean active) {
        var c=new AttitudeCriterion();c.setId(id);c.setConfiguration(configuration);c.setName("Criterion "+id);c.setDescription("Description "+id);
        c.setCriterionType(type);c.setEvaluationFormat(format);c.setActive(active);c.setDisplayOrder(id.intValue());configuration.getCriteria().add(c);
    }
    AttitudeAssessmentRequest request() {var r=new AttitudeAssessmentRequest();r.setReviewPeriodId(1L);return r;}
    AttitudeAssessmentRequest.Answer answer(Long id,Integer point) {var a=new AttitudeAssessmentRequest.Answer();a.setCriterionId(id);a.setSelfPoint(point);return a;}
    void complete() {var r=request();r.setItems(List.of(answer(11L,3),answer(12L,5)));service.create(r,owner);}
    @AfterEach void cleanup() {if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();}

    @Test void virtualDraftUsesBoundConfigurationAndRecordedRoleWithoutWrites() {
        employee.setRole(new Role());var dto=service.mine(1L,owner);
        assertNull(dto.getId());assertNull(dto.getCreatedAt());assertEquals(10L,dto.getConfigurationId());
        assertEquals("Recorded role",dto.getRoleName());assertEquals(AttitudeEvaluationFormat.SALES,dto.getEvaluationFormat());
        assertEquals(List.of(11L,12L),dto.getItems().stream().map(AttitudeAssessmentDto.Item::getCriterionId).toList());
        assertEquals(List.of(5,4,3,2,1),dto.getRatingDefinitions().stream().map(AttitudeConfigurationRequest.Rating::getPoint).toList());
        assertTrue(dto.isCanSaveDraft());assertFalse(dto.isCanSubmit());verify(assessments,never()).saveAndFlush(any());
    }
    @Test void annualAssessmentAvailableOnOpeningRegardlessOfKpiFrequency() {
        for(var frequency:ReviewFrequency.values()) {participant.setReviewFrequency(frequency);assertTrue(service.mine(1L,owner).isAvailable());}
        period.setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);assertFalse(service.mine(1L,owner).isCanSaveDraft());
        assertThrows(BadRequestException.class,()->service.create(request(),owner));
    }
    @Test void missingBindingIsFriendlyAndDoesNotCreateOrUseGlobalDefaults() {
        period.setAttitudeConfiguration(null);var dto=service.mine(1L,owner);
        assertFalse(dto.isAvailable());assertEquals("Attitude Evaluation Not Yet Available",dto.getAvailabilityTitle());
        assertEquals("The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later.",dto.getAvailabilityMessage());
        assertNull(dto.getStatus());assertTrue(dto.getItems().isEmpty());
        assertThrows(BadRequestException.class,()->service.create(request(),owner));verify(assessments,never()).saveAndFlush(any());
    }
    @Test void unpublishedConfigurationCannotEnableAssessment() {
        configuration.setStatus(AttitudeConfigurationStatus.DRAFT);assertFalse(service.mine(1L,owner).isAvailable());
        assertThrows(BadRequestException.class,()->service.create(request(),owner));
    }
    @Test void missingRoleMappingDoesNotInferFormatFromRoleName() {
        configuration.getRoleMappings().clear();participant.getRole().setName("Sales Manager");
        var dto=service.mine(1L,owner);assertTrue(dto.getAvailabilityMessage().contains("Job Role"));assertFalse(dto.isCanSubmit());
        assertThrows(BadRequestException.class,()->service.create(request(),owner));
        participant.setRole(null);assertFalse(service.mine(1L,owner).isAvailable());
    }
    @Test void allFormatsIncludeSharedAndOnlyOwnActiveCriteria() {
        criterion(15L,AttitudeCriterionType.FORMAT_SPECIFIC,AttitudeEvaluationFormat.OTHERS,true);
        for(var format:AttitudeEvaluationFormat.values()) {
            configuration.getRoleMappings().get(0).setEvaluationFormat(format);
            var dto=service.mine(1L,owner);assertEquals(2,dto.getItems().size());assertEquals(11L,dto.getItems().get(0).getCriterionId());
            assertEquals(format,dto.getEvaluationFormat());
        }
    }
    @Test void noApplicableCriteriaRemainsUnavailable() {
        configuration.getCriteria().clear();assertFalse(service.mine(1L,owner).isAvailable());
        assertThrows(BadRequestException.class,()->service.create(request(),owner));
    }
    @Test void incompleteDraftCanSaveWithoutSuperiorAndOptionalComments() {
        employee.setManager(null);var dto=service.create(request(),owner);assertEquals(AttitudeAssessmentStatus.DRAFT,dto.getStatus());
        assertEquals(2,dto.getItems().size());assertNull(dto.getItems().get(0).getSelfPoint());
        assertThrows(BadRequestException.class,()->service.submit(20L,owner));verifyNoInteractions(email);
    }
    @Test void partialDraftUpdatePreservesOmittedAnswersAndTrimsComments() {
        var r=request();var first=answer(11L,4);first.setSelfComment("  Reflection  ");r.setItems(List.of(first));
        service.create(r,owner);r.setItems(List.of(answer(12L,5)));var dto=service.update(20L,r,owner);
        assertEquals(4,dto.getItems().get(0).getSelfPoint());assertEquals("Reflection",dto.getItems().get(0).getSelfComment());assertTrue(dto.isCanSubmit());
    }
    @Test void missingPointsIdentifyCriteriaAndDoNotSubmitOrNotify() {
        service.create(request(),owner);var error=assertThrows(BadRequestException.class,()->service.submit(20L,owner));
        assertTrue(error.getMessage().contains("Criterion 11"));assertTrue(error.getMessage().contains("Criterion 12"));
        assertEquals(AttitudeAssessmentStatus.DRAFT,assessment.getStatus());verifyNoInteractions(email);
    }
    @Test void deadlineInclusiveAndAfterDeadlineAllowedWithStoredLateness() {
        complete();date("2027-12-05");var dto=service.submit(20L,owner);assertFalse(dto.getSubmittedLate());assertFalse(dto.isOverdue());
        assertEquals(AttitudeAssessmentStatus.PENDING_REVIEW,dto.getStatus());assertEquals(superiorId,dto.getSubmittedToSuperiorId());
        assertNull(assessment.getAttitudeScore());assertNull(assessment.getReviewedAt());
    }
    @Test void annualEndDateDoesNotCloseAssessmentAndLateFlagSurvivesLaterDeadlineChange() {
        complete();date("2028-01-02");assertTrue(service.get(20L,owner).isOverdue());assertTrue(service.submit(20L,owner).getSubmittedLate());
        period.setAttitudeSelfAssessmentDeadline(LocalDate.of(2028,1,10));assertTrue(service.get(20L,owner).getSubmittedLate());
    }
    @Test void submittedAnswersCannotBeEditedOrSubmittedTwice() {
        complete();service.submit(20L,owner);assertFalse(service.get(20L,owner).isCanSaveDraft());
        assertThrows(BadRequestException.class,()->service.update(20L,request(),owner));assertThrows(BadRequestException.class,()->service.submit(20L,owner));
        verify(email,times(1)).sendAttitudeSelfAssessmentSubmittedEmail(anyString(),anyString(),anyString(),any());
    }
    @Test void closedPeriodRemainsReadableButRejectsAllMutations() {
        complete();period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertEquals(2,service.get(20L,owner).getItems().size());assertFalse(service.get(20L,owner).isCanSaveDraft());
        assertThrows(BadRequestException.class,()->service.update(20L,request(),owner));assertThrows(BadRequestException.class,()->service.submit(20L,owner));
    }
    @Test void duplicateAnnualAssessmentCannotBeCreated() {
        service.create(request(),owner);assertThrows(BadRequestException.class,()->service.create(request(),owner));verify(assessments,times(1)).saveAndFlush(any());
    }
    @Test void foreignInactiveOrOtherFormatCriteriaCannotBeAnswered() {
        for(Long id:List.of(13L,14L,999L)) {var r=request();r.setItems(List.of(answer(id,3)));
            assertThrows(AccessDeniedException.class,()->service.create(r,owner));}
        verify(assessments,never()).saveAndFlush(any());
    }
    @Test void duplicateMissingAndInvalidAnswersRejectedBeforeAnyChanges() {
        complete();var r=request();r.setItems(List.of(answer(11L,1),answer(12L,6)));
        assertThrows(BadRequestException.class,()->service.update(20L,r,owner));assertEquals(3,assessment.getItems().get(0).getSelfPoint());
        for(Integer point:List.of(0,6)) {r.setItems(List.of(answer(11L,point)));assertThrows(BadRequestException.class,()->service.update(20L,r,owner));}
        r.setItems(List.of(answer(11L,3),answer(11L,4)));assertThrows(BadRequestException.class,()->service.update(20L,r,owner));
        r.setItems(List.of(answer(null,3)));assertThrows(BadRequestException.class,()->service.update(20L,r,owner));
        r.setItems(Arrays.asList((AttitudeAssessmentRequest.Answer)null));assertThrows(BadRequestException.class,()->service.update(20L,r,owner));
        r.setItems(null);assertThrows(BadRequestException.class,()->service.update(20L,r,owner));
        var longComment=answer(11L,4);longComment.setSelfComment("x".repeat(10001));r.setItems(List.of(longComment));assertThrows(BadRequestException.class,()->service.update(20L,r,owner));
    }
    @Test void periodAndCriterionSetCannotBeSwitched() {
        complete();var r=request();r.setReviewPeriodId(2L);assertThrows(BadRequestException.class,()->service.update(20L,r,owner));
        assessment.getItems().remove(0);assertThrows(BadRequestException.class,()->service.submit(20L,owner));
    }
    @Test void missingInactiveDeletedOrSelfSuperiorBlocksOnlySubmission() {
        for(int kind=0;kind<4;kind++) {
            employee.setManager(superior);superior.setDeleted(false);superior.setAccountStatus(StaffAccountStatus.ACTIVE);
            switch(kind) {case 0->employee.setManager(null);case 1->superior.setDeleted(true);
                case 2->superior.setAccountStatus(StaffAccountStatus.INACTIVE);case 3->employee.setManager(employee);}
            if(assessment==null) complete();assertTrue(service.get(20L,owner).isCanSaveDraft());
            assertThrows(BadRequestException.class,()->service.submit(20L,owner));
        }
    }
    @Test void foreignOwnershipAndUnenrolledAccountsDeniedIncludingSuperior() {
        complete();assertThrows(AccessDeniedException.class,()->service.mine(1L,outsider));
        assertThrows(AccessDeniedException.class,()->service.get(20L,outsider));assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        assertThrows(AccessDeniedException.class,()->service.update(20L,request(),outsider));assertThrows(AccessDeniedException.class,()->service.submit(20L,outsider));
    }
    @Test void inactiveOrDeletedEmployeeCannotAccessAnyEndpoint() {
        complete();employee.setDeleted(true);assertThrows(AccessDeniedException.class,()->service.get(20L,owner));
        employee.setDeleted(false);employee.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class,()->service.periods(owner));assertThrows(AccessDeniedException.class,()->service.mine(1L,owner));
        assertThrows(AccessDeniedException.class,()->service.create(request(),owner));
    }
    @Test void missingDeadlineBlocksSafelyRatherThanInventingOne() {
        period.setAttitudeSelfAssessmentDeadline(null);assertFalse(service.mine(1L,owner).isCanSaveDraft());
        assertThrows(BadRequestException.class,()->service.create(request(),owner));
    }
    @Test void newerPublishedEditionDoesNotAffectBoundAssessment() {
        complete();var newer=new AttitudeConfiguration();newer.setId(99L);newer.setStatus(AttitudeConfigurationStatus.PUBLISHED);
        var dto=service.mine(1L,owner);assertEquals(10L,dto.getConfigurationId());assertEquals("Bound edition",dto.getConfigurationName());
        assertEquals(2,dto.getItems().size());assertTrue(service.submit(20L,owner).isAvailable());
    }
    @Test void notificationRunsOnlyAfterSuccessfulCommitAndDoesNotUndoSubmissionIfMailFails() {
        complete();TransactionSynchronizationManager.initSynchronization();service.submit(20L,owner);verifyNoInteractions(email);
        doThrow(new IllegalStateException("Mail offline")).when(email).sendAttitudeSelfAssessmentSubmittedEmail(anyString(),anyString(),anyString(),any());
        assertDoesNotThrow(()->TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
    }
    @Test void persistenceFailureDoesNotScheduleNotification() {
        complete();TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("Database failure")).when(assessments).saveAndFlush(assessment);
        assertThrows(IllegalStateException.class,()->service.submit(20L,owner));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());verifyNoInteractions(email);
    }
}
