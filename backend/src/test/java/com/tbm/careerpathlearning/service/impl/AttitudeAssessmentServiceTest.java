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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
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
        employee=new Staff();employee.setId(owner);employee.setEmail("employee@example.test");employee.setAccountStatus(StaffAccountStatus.ACTIVE);employee.setManager(superior);
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
            assessment.getItems().forEach(i->{if(i.getId()==null)i.setId(i.getCriterion().getId()+100);});
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
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();}

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
    void reviewer(UUID actor,String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(authority))));
    }
    void submitted() {complete();service.submit(20L,owner);reviewer(superiorId,"CAN_REVIEW_ATTITUDE_EVALUATION");clearInvocations(email);}
    AttitudeSuperiorAssessmentRequest superiorRequest(Integer first,Integer second) {
        var request=new AttitudeSuperiorAssessmentRequest();
        var a=new AttitudeSuperiorAssessmentRequest.Answer();a.setItemId(111L);a.setSuperiorPoint(first);
        var b=new AttitudeSuperiorAssessmentRequest.Answer();b.setItemId(112L);b.setSuperiorPoint(second);
        request.setItems(List.of(a,b));return request;
    }
    @Test void reviewQueueIsScopedAndDoesNotIncludeEmployeeDraftsOrWriteOnRead() {
        submitted();when(assessments.findReviewAssessments(superiorId,List.of(AttitudeAssessmentStatus.PENDING_REVIEW,AttitudeAssessmentStatus.REVIEWED),1L))
                .thenReturn(List.of(assessment));
        clearInvocations(assessments);var rows=service.reviews(1L,null,superiorId);
        assertEquals(1,rows.size());assertEquals("Amir",rows.get(0).getEmployeeName());assertTrue(rows.get(0).isCanReview());
        assertFalse(rows.get(0).isSuperiorDraftSaved());assertNull(rows.get(0).getAttitudeScore());
        verify(assessments,never()).saveAndFlush(any());
        assertThrows(BadRequestException.class,()->service.reviews(null,AttitudeAssessmentStatus.DRAFT,superiorId));
        employee.setManager(null);assertTrue(service.reviews(1L,null,superiorId).isEmpty());
    }
    @Test void superiorDraftMayBeEmptyOrIncompleteWithoutChangingSelfAnswersOrOfficialScore() {
        submitted();var empty=new AttitudeSuperiorAssessmentRequest();var dto=service.saveSuperiorDraft(20L,empty,superiorId);
        assertTrue(dto.isSuperiorDraftSaved());assertEquals(AttitudeAssessmentStatus.PENDING_REVIEW,dto.getStatus());
        assertNull(dto.getAttitudeScore());assertTrue(dto.isCanSaveSuperiorDraft());assertFalse(dto.isCanCompleteReview());
        assertEquals(3,dto.getItems().get(0).getSelfPoint());assertEquals(5,dto.getItems().get(1).getSelfPoint());
        var request=superiorRequest(4,null);request.getItems().get(0).setSuperiorComment("  Observed behaviour  ");
        dto=service.saveSuperiorDraft(20L,request,superiorId);assertEquals("Observed behaviour",dto.getItems().get(0).getSuperiorComment());
        assertTrue(dto.getReviewBlockers().stream().anyMatch(s->s.contains("Criterion 12")));verifyNoInteractions(email);
    }
    @Test void partialSuperiorUpdatesPreserveOmittedAnswersAndEmployeeCannotSeeUnfinishedReview() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);
        var request=superiorRequest(null,3);request.setItems(List.of(request.getItems().get(1)));
        var dto=service.saveSuperiorDraft(20L,request,superiorId);assertEquals(4,dto.getItems().get(0).getSuperiorPoint());
        assertEquals(3,dto.getItems().get(1).getSuperiorPoint());assertTrue(dto.isCanCompleteReview());
        var employeeView=service.get(20L,owner);assertNull(employeeView.getItems().get(0).getSuperiorPoint());
        assertNull(employeeView.getItems().get(0).getSuperiorComment());assertFalse(employeeView.isCanSaveSuperiorDraft());
        assertFalse(employeeView.isCanCompleteReview());assertTrue(employeeView.getReviewBlockers().isEmpty());assertFalse(employeeView.isSuperiorDraftSaved());
    }
    @Test void completedScoreUsesEqualSuperiorWeightsOnlyAndEmployeeSeesFinalResults() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,3),superiorId);
        assessment.getItems().forEach(i->i.setSelfPoint(1));var dto=service.completeReview(20L,superiorId);
        assertEquals(new BigDecimal("70.0000"),dto.getAttitudeScore());assertEquals(AttitudeAssessmentStatus.REVIEWED,dto.getStatus());
        assertEquals(superiorId,dto.getReviewedBy());assertNotNull(dto.getReviewedAt());assertFalse(dto.getReviewedLate());
        assertFalse(dto.isCanCompleteReview());assertFalse(dto.isCanSaveSuperiorDraft());
        assertEquals(4,service.get(20L,owner).getItems().get(0).getSuperiorPoint());
        verify(email).sendAttitudeAssessmentReviewedEmail("employee@example.test","Amir","Annual Review",Locale.ENGLISH);
    }
    @Test void equalAverageHasDecimalPrecisionAndDoesNotDependOnEmployeeLevelOrFormat() {
        submitted();var criterion=configuration.getCriteria().get(1);criterion.setEvaluationFormat(null);
        criterion.setCriterionType(AttitudeCriterionType.SHARED_CORE_VALUE);
        var extra=new AttitudeAssessmentItem();extra.setId(115L);extra.setAssessment(assessment);
        criterion(15L,AttitudeCriterionType.SHARED_CORE_VALUE,null,true);
        extra.setCriterion(configuration.getCriteria().get(4));extra.setSuperiorPoint(3);extra.setSelfPoint(5);assessment.getItems().add(extra);
        for(var format:AttitudeEvaluationFormat.values()) {
            assessment.setEvaluationFormat(format);configuration.getRoleMappings().get(0).setEvaluationFormat(format);
            // Only shared criteria apply for this test across all three formats.
            configuration.getCriteria().get(2).setActive(false);
            assessment.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);
            service.saveSuperiorDraft(20L,superiorRequest(4,3),superiorId);
            assertEquals(new BigDecimal("66.6667"),service.completeReview(20L,superiorId).getAttitudeScore());
        }
    }
    @Test void scoreEndpointsAreTwentyAndOneHundredPercent() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(1,1),superiorId);
        assertEquals(new BigDecimal("20.0000"),service.completeReview(20L,superiorId).getAttitudeScore());
        assessment.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);service.saveSuperiorDraft(20L,superiorRequest(5,5),superiorId);
        assertEquals(new BigDecimal("100.0000"),service.completeReview(20L,superiorId).getAttitudeScore());
    }
    @Test void completionRequiresEveryApplicableCriterionAndSuperiorPoint() {
        submitted();assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));
        service.saveSuperiorDraft(20L,superiorRequest(4,null),superiorId);
        assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));
        assessment.getItems().remove(1);assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));
        assessment.getItems().clear();assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));verifyNoInteractions(email);
    }
    @Test void invalidSuperiorAnswersRejectedBeforeChangingAnyAnswer() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);
        var request=superiorRequest(1,6);assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        assertEquals(4,assessment.getItems().get(0).getSuperiorPoint());
        request.getItems().get(1).setSuperiorPoint(0);assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        request.getItems().get(1).setItemId(111L);assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        request.getItems().get(1).setItemId(999L);assertThrows(AccessDeniedException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        request.getItems().get(1).setItemId(null);assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        request.setItems(Arrays.asList((AttitudeSuperiorAssessmentRequest.Answer)null));assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        request.setItems(null);assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
        assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,null,superiorId));
        request.setItems(superiorRequest(4,5).getItems());request.getItems().get(0).setSuperiorComment("x".repeat(10001));
        assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,request,superiorId));
    }
    @Test void reviewRequiresDedicatedPermissionAndCurrentSuperiorAndSubmissionRoute() {
        submitted();reviewer(superiorId,"CAN_MANAGE_ATTITUDE_CONFIGURATION");
        assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        assertThrows(AccessDeniedException.class,()->service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId));
        assertThrows(AccessDeniedException.class,()->service.reviews(null,null,superiorId));
        reviewer(outsider,"CAN_REVIEW_ATTITUDE_EVALUATION");assertThrows(AccessDeniedException.class,()->service.get(20L,outsider));
        assertThrows(AccessDeniedException.class,()->service.completeReview(20L,outsider));
        reviewer(owner,"CAN_REVIEW_ATTITUDE_EVALUATION");assertThrows(AccessDeniedException.class,()->service.completeReview(20L,owner));
        employee.setManager(staff.findById(outsider).orElseThrow());
        reviewer(superiorId,"CAN_REVIEW_ATTITUDE_EVALUATION");assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        reviewer(outsider,"CAN_REVIEW_ATTITUDE_EVALUATION");assertThrows(AccessDeniedException.class,()->service.get(20L,outsider));
        assertEquals(superiorId,assessment.getSubmittedToSuperiorId());
    }
    @Test void employeeDraftCannotBeReadOrReviewedBySuperiorEvenWithPermission() {
        complete();reviewer(superiorId,"CAN_REVIEW_ATTITUDE_EVALUATION");
        assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        assertThrows(AccessDeniedException.class,()->service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId));
        assertThrows(AccessDeniedException.class,()->service.completeReview(20L,superiorId));
    }
    @Test void inactiveOrDeletedSuperiorCannotReadOrReview() {
        submitted();superior.setDeleted(true);assertThrows(AccessDeniedException.class,()->service.get(20L,superiorId));
        assertThrows(AccessDeniedException.class,()->service.completeReview(20L,superiorId));
        superior.setDeleted(false);superior.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class,()->service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId));
        assertThrows(AccessDeniedException.class,()->service.reviews(null,null,superiorId));
    }
    @Test void reviewedAssessmentCannotBeMutatedOrCompletedTwice() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);service.completeReview(20L,superiorId);
        assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,superiorRequest(1,1),superiorId));
        assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));
        assertThrows(BadRequestException.class,()->service.update(20L,request(),owner));
        verify(email,times(1)).sendAttitudeAssessmentReviewedEmail(anyString(),anyString(),anyString(),any());
    }
    @Test void closedOrUpcomingPeriodIsReadableButReviewIsDisabled() {
        submitted();for(var status:List.of(AnnualKpiReviewPeriodStatus.CLOSED,AnnualKpiReviewPeriodStatus.UPCOMING)) {
            period.setStatus(status);assertFalse(service.get(20L,superiorId).isCanSaveSuperiorDraft());
            assertFalse(service.get(20L,superiorId).isCanCompleteReview());
            assertThrows(BadRequestException.class,()->service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId));
            assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));
        }
    }
    @Test void deadlineDayIsOnTimeAndReviewAfterAnnualEndIsAllowedAndRecordedLate() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);date("2027-12-15");
        assertFalse(service.get(20L,superiorId).isSuperiorOverdue());assertFalse(service.completeReview(20L,superiorId).getReviewedLate());
        assessment.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);date("2028-01-02");
        assertTrue(service.get(20L,superiorId).isSuperiorOverdue());assertTrue(service.completeReview(20L,superiorId).getReviewedLate());
        period.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(2028,1,20));
        assertTrue(service.get(20L,owner).getReviewedLate());assertFalse(service.get(20L,owner).isSuperiorOverdue());
    }
    @Test void missingSuperiorDeadlineBlocksCompletionWithoutThrowingOnRead() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);period.setSuperiorAttitudeEvaluationDeadline(null);
        assertFalse(service.get(20L,superiorId).isCanCompleteReview());assertThrows(BadRequestException.class,()->service.completeReview(20L,superiorId));
    }
    @Test void reviewNotificationWaitsForCommitAndMailFailureDoesNotUndoCompletion() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);TransactionSynchronizationManager.initSynchronization();
        service.completeReview(20L,superiorId);verifyNoInteractions(email);
        doThrow(new IllegalStateException("Mail offline")).when(email).sendAttitudeAssessmentReviewedEmail(anyString(),anyString(),anyString(),any());
        assertDoesNotThrow(()->TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
    }
    @Test void reviewPersistenceFailureDoesNotScheduleNotificationAndUsesSubmissionLocks() {
        submitted();service.saveSuperiorDraft(20L,superiorRequest(4,5),superiorId);TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("Database failure")).when(assessments).saveAndFlush(assessment);
        assertThrows(IllegalStateException.class,()->service.completeReview(20L,superiorId));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());verifyNoInteractions(email);
        verify(assessments,atLeast(2)).lockById(20L);verify(periods,atLeast(2)).lockConfiguration();
    }
}
