package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.tbm.careerpathlearning.dto.ReviewPeriodClosureDto.Code;
import com.tbm.careerpathlearning.exception.*;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnualReviewPeriodClosureServiceTest {
    final AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    final ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    final ReviewCheckpointRepository checkpoints=mock(ReviewCheckpointRepository.class);
    final KpiAssessmentRepository kpis=mock(KpiAssessmentRepository.class);
    final AttitudeAssessmentRepository attitudes=mock(AttitudeAssessmentRepository.class);
    final AnnualAppraisalReadinessService readiness=mock(AnnualAppraisalReadinessService.class);
    final StaffRepository staff=mock(StaffRepository.class);
    final RoleAuthorityRepository permissions=mock(RoleAuthorityRepository.class);
    final UUID actor=UUID.randomUUID();
    final Clock clock=Clock.fixed(Instant.parse("2028-01-01T00:00:00Z"),ZoneOffset.UTC);
    AnnualReviewPeriodClosureService service;
    AnnualKpiReviewPeriod period;ReviewPeriodParticipant participant;ReviewCheckpoint checkpoint;
    KpiAssessment assessment;AttitudeAssessment attitude;Staff admin;AnnualAppraisalSnapshotDto snapshot;

    @BeforeEach void setup() {
        service=new AnnualReviewPeriodClosureService(periods,participants,checkpoints,kpis,attitudes,readiness,
                new AnnualReviewPeriodConfigurationValidator(),staff,permissions,clock);
        var role=new Role();role.setId(10L);admin=new Staff();admin.setId(actor);admin.setName("HR Administrator");
        admin.setRole(role);admin.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(actor)).thenReturn(Optional.of(admin));when(permissions.existsByRoleIdAndAuthorityName(10L,AuthorityName.CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD)).thenReturn(true);
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setStartDate(LocalDate.of(2027,1,1));period.setEndDate(LocalDate.of(2027,12,31));
        period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);period.setParticipantsSnapshottedAt(OffsetDateTime.now(clock));
        var configuration=new AttitudeConfiguration();configuration.setId(4L);configuration.setStatus(AttitudeConfigurationStatus.PUBLISHED);period.setAttitudeConfiguration(configuration);
        var allocation=new ReviewPeriodEmployeeLevelConfiguration();allocation.setId(5L);allocation.setReviewPeriod(period);
        allocation.setCompanyKpiWeight(BigDecimal.ZERO);allocation.setDepartmentKpiWeight(BigDecimal.ZERO);allocation.setIndividualKpiWeight(new BigDecimal("100"));
        participant=new ReviewPeriodParticipant();participant.setId(2L);participant.setReviewPeriod(period);participant.setStaffName("Fictional Employee");participant.setDepartmentName("Test Department");
        participant.setEmployeeLevelConfiguration(allocation);participant.setReviewFrequency(ReviewFrequency.ANNUALLY);participant.setRole(role);
        var employee=new Staff();employee.setId(UUID.randomUUID());employee.setRole(role);employee.setAccountStatus(StaffAccountStatus.ACTIVE);participant.setStaff(employee);
        var mapping=new AttitudeRoleFormatMapping();mapping.setConfiguration(configuration);mapping.setRole(role);mapping.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);
        configuration.getRoleMappings().add(mapping);
        var criterion=new AttitudeCriterion();criterion.setId(9L);criterion.setConfiguration(configuration);criterion.setName("Test");criterion.setDescription("Test");
        criterion.setCriterionType(AttitudeCriterionType.SHARED_CORE_VALUE);configuration.getCriteria().add(criterion);
        checkpoint=new ReviewCheckpoint();checkpoint.setId(6L);checkpoint.setReviewPeriod(period);checkpoint.setReviewFrequency(ReviewFrequency.ANNUALLY);
        checkpoint.setStartDate(period.getStartDate());checkpoint.setEndDate(period.getEndDate());checkpoint.setSequenceNumber(1);
        assessment=new KpiAssessment();assessment.setId(7L);assessment.setParticipant(participant);assessment.setReviewPeriodId(1L);assessment.setCheckpoint(checkpoint);assessment.setStatus(KpiAssessmentStatus.REVIEWED);
        assessment.setReviewFrequency(ReviewFrequency.ANNUALLY);assessment.setCheckpointScore(new BigDecimal("80"));
        attitude=new AttitudeAssessment();attitude.setId(8L);attitude.setParticipant(participant);attitude.setReviewPeriodId(1L);attitude.setConfiguration(configuration);attitude.setStatus(AttitudeAssessmentStatus.REVIEWED);
        attitude.setAttitudeScore(new BigDecimal("80"));attitude.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);
        var answer=new AttitudeAssessmentItem();answer.setAssessment(attitude);answer.setCriterion(criterion);answer.setConfigurationId(4L);answer.setSelfPoint(4);answer.setSuperiorPoint(4);attitude.getItems().add(answer);
        var item=new KpiItemDto();item.setWeightage(new BigDecimal("100"));
        var breakdown=new KpiScoreBreakdownDto(7L,new BigDecimal("80"),List.of(new KpiScoreBreakdownDto.Level(KpiLevel.INDIVIDUAL,new BigDecimal("100"),1,null,null,KpiScoreBreakdownDto.Availability.AVAILABLE,new BigDecimal("80"),List.of(new KpiScoreBreakdownDto.Item(item,null,4)))));
        snapshot=new AnnualAppraisalSnapshotDto(2L,1L,employee.getId(),ReviewFrequency.ANNUALLY,AnnualKpiConsolidationMethod.FINAL_CHECKPOINT,new BigDecimal("50"),new BigDecimal("50"),new BigDecimal("80"),new BigDecimal("80"),new BigDecimal("80"),
                List.of(new AnnualAppraisalSnapshotDto.Checkpoint(6L,1,checkpoint.getStartDate(),checkpoint.getEndDate(),7L,KpiAssessmentStatus.REVIEWED,new BigDecimal("80"),null,List.of(),breakdown)),
                new AnnualAppraisalSnapshotDto.Attitude(8L,4L,AttitudeEvaluationFormat.OTHERS,AttitudeAssessmentStatus.REVIEWED,null,List.of(new AnnualAppraisalSnapshotDto.Criterion(9L,"Test", "Test",4))));
        when(periods.findById(1L)).thenReturn(Optional.of(period));when(participants.findAllByReviewPeriodId(1L)).thenReturn(List.of(participant));
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L,ReviewFrequency.ANNUALLY)).thenReturn(List.of(checkpoint));
        when(kpis.findPerformanceAssessments(List.of(2L))).thenReturn(List.of(assessment));when(attitudes.findPerformanceAssessments(List.of(2L))).thenReturn(List.of(attitude));
        when(readiness.evaluate(participant)).thenReturn(new AnnualAppraisalReadinessDto(true,List.of(),snapshot));
    }
    @Test void readinessDoesNotWriteAndClosureRecordsOneManualTransition() {
        var ready=service.readiness(1L,actor);assertThat(ready.canClose()).isTrue();assertThat(ready.completedEmployees()).isEqualTo(1);
        verify(periods,never()).saveAndFlush(any());
        var closed=service.close(1L,actor);assertThat(closed.status()).isEqualTo(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThat(closed.closedByName()).isEqualTo("HR Administrator");assertThat(closed.closedAt()).isNotNull();
        var originalTime=period.getClosedAt();service.close(1L,actor);assertThat(period.getClosedAt()).isEqualTo(originalTime);
        verify(periods,times(1)).saveAndFlush(period);verify(kpis,never()).saveAndFlush(any());verify(attitudes,never()).saveAndFlush(any());
    }
    List<Code> codes() {return service.readiness(1L,actor).blockers().stream().map(ReviewPeriodClosureDto.Blocker::code).toList();}
    AnnualReviewPeriodClosureService at(String instant) {
        return new AnnualReviewPeriodClosureService(periods,participants,checkpoints,kpis,attitudes,readiness,
                new AnnualReviewPeriodConfigurationValidator(),staff,permissions,Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));
    }
    AnnualAppraisalSnapshotDto withScores(BigDecimal kpi,BigDecimal attitudeScore,BigDecimal finalScore,List<AnnualAppraisalSnapshotDto.Checkpoint> sources) {
        return new AnnualAppraisalSnapshotDto(2L,1L,participant.getStaff().getId(),participant.getReviewFrequency(),period.getAnnualKpiConsolidationMethod(),period.getKpiPerformanceWeight(),period.getAttitudeEvaluationWeight(),kpi,attitudeScore,finalScore,sources,snapshot.attitude());
    }
    @Test void closureUsesUtcPlusEightBoundaryNotMachineTimezoneOrAssessmentDeadlines() {
        assertThat(at("2027-12-31T15:59:59Z").readiness(1L,actor).canClose()).isFalse();
        assertThat(at("2027-12-31T16:00:00Z").readiness(1L,actor).canClose()).isTrue();
        assertThat(service.readiness(1L,actor).earliestCloseDate()).isEqualTo(LocalDate.of(2028,1,1));
        assertThat(period.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.OPEN);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"DRAFT","UPCOMING"})
    void nonOpenPeriodsCannotClose(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);assertThat(codes()).contains(Code.PERIOD_NOT_OPEN);
        assertThatThrownBy(()->service.close(1L,actor)).isInstanceOf(ReviewPeriodClosureBlockedException.class);verify(periods,never()).saveAndFlush(any());
    }
    @ParameterizedTest @EnumSource(AnnualKpiConsolidationMethod.class)
    void allScheduledCheckpointsRequiredForBothConsolidationMethods(AnnualKpiConsolidationMethod method) {
        period.setAnnualKpiConsolidationMethod(method);
        var earlier=new ReviewCheckpoint();earlier.setId(60L);earlier.setReviewPeriod(period);earlier.setStartDate(period.getStartDate());earlier.setEndDate(LocalDate.of(2027,6,30));
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L,ReviewFrequency.ANNUALLY)).thenReturn(List.of(earlier,checkpoint));
        var state=service.readiness(1L,actor);assertThat(state.canClose()).isFalse();assertThat(state.kpiReviews().required()).isEqualTo(2);
        assertThat(state.kpiReviews().completed()).isEqualTo(1);assertThat(state.blockers()).anySatisfy(b->{assertThat(b.code()).isEqualTo(Code.KPI_SELF_ASSESSMENT);assertThat(b.checkpointId()).isEqualTo(60L);});
    }
    @Test void missingVersusPendingKpiAndAttitudeAreClassifiedAsDifferentTasks() {
        when(kpis.findPerformanceAssessments(anyList())).thenReturn(List.of());when(attitudes.findPerformanceAssessments(anyList())).thenReturn(List.of());
        assertThat(codes()).contains(Code.KPI_SELF_ASSESSMENT,Code.ATTITUDE_SELF_ASSESSMENT);
        when(kpis.findPerformanceAssessments(anyList())).thenReturn(List.of(assessment));when(attitudes.findPerformanceAssessments(anyList())).thenReturn(List.of(attitude));
        assessment.setStatus(KpiAssessmentStatus.PENDING_REVIEW);attitude.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);
        assertThat(codes()).contains(Code.KPI_SUPERIOR_REVIEW,Code.ATTITUDE_SUPERIOR_REVIEW).doesNotContain(Code.KPI_SELF_ASSESSMENT,Code.ATTITUDE_SELF_ASSESSMENT);
    }
    @Test void genuineZeroIsCompleteButMissingScoreDoesNotBecomeZero() {
        var old=snapshot.checkpoints().get(0);var zero=new AnnualAppraisalSnapshotDto.Checkpoint(old.checkpointId(),old.sequenceNumber(),old.startDate(),old.endDate(),old.assessmentId(),old.status(),BigDecimal.ZERO,old.reviewedAt(),old.versions(),old.breakdown());
        attitude.setAttitudeScore(BigDecimal.ZERO);
        when(readiness.evaluate(participant)).thenReturn(new AnnualAppraisalReadinessDto(true,List.of(),withScores(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,List.of(zero))));
        assertThat(service.readiness(1L,actor).canClose()).isTrue();
        var missing=new AnnualAppraisalSnapshotDto.Checkpoint(old.checkpointId(),old.sequenceNumber(),old.startDate(),old.endDate(),old.assessmentId(),old.status(),null,old.reviewedAt(),old.versions(),old.breakdown());
        when(readiness.evaluate(participant)).thenReturn(new AnnualAppraisalReadinessDto(false,List.of("Missing"),withScores(null,BigDecimal.ZERO,null,List.of(missing))));
        assertThat(codes()).contains(Code.KPI_SCORE);
    }
    @Test void missingCentralResultsRetainTheirOwnBlockersAndZeroAllocationDoesNotRequirePublication() {
        var old=snapshot.checkpoints().get(0);
        var pending=new KpiScoreBreakdownDto(7L,null,List.of(
                new KpiScoreBreakdownDto.Level(KpiLevel.COMPANY,new BigDecimal("15"),1,null,null,KpiScoreBreakdownDto.Availability.PENDING,null,List.of()),
                new KpiScoreBreakdownDto.Level(KpiLevel.DEPARTMENT,BigDecimal.ZERO,null,null,null,KpiScoreBreakdownDto.Availability.NOT_APPLICABLE,BigDecimal.ZERO,List.of())));
        var source=new AnnualAppraisalSnapshotDto.Checkpoint(old.checkpointId(),old.sequenceNumber(),old.startDate(),old.endDate(),old.assessmentId(),old.status(),null,old.reviewedAt(),old.versions(),pending);
        when(readiness.evaluate(participant)).thenReturn(new AnnualAppraisalReadinessDto(false,List.of("Waiting for Company"),withScores(null,new BigDecimal("80"),null,List.of(source))));
        assertThat(codes()).contains(Code.COMPANY_RESULT).doesNotContain(Code.DEPARTMENT_RESULT);
    }
    @Test void invalidAllocationDoesNotEraseAnIndependentlyCompletedAttitudeCount() {
        participant.getEmployeeLevelConfiguration().setIndividualKpiWeight(new BigDecimal("99"));
        var state=service.readiness(1L,actor);assertThat(state.canClose()).isFalse();assertThat(state.attitudeEvaluations().completed()).isEqualTo(1);
        assertThat(state.blockers()).anyMatch(b->b.code()==Code.CONFIGURATION);
    }
    @Test void missingAttitudeConfigurationDoesNotHideAnIndependentlyCompletedKpiCheckpoint() {
        period.setAttitudeConfiguration(null);
        var state=service.readiness(1L,actor);assertThat(state.canClose()).isFalse();assertThat(state.kpiReviews().completed()).isEqualTo(1);
        assertThat(state.attitudeEvaluations().completed()).isZero();
        assertThat(state.blockers()).anyMatch(b->b.code()==Code.CONFIGURATION).anyMatch(b->b.code()==Code.ATTITUDE_SCORE);
    }
    @Test void reviewedAttitudeMissingAnExpectedCriterionCannotClose() {
        var configuration=period.getAttitudeConfiguration();
        var additional=new AttitudeCriterion();additional.setId(10L);additional.setConfiguration(configuration);additional.setName("Additional core value");
        additional.setCriterionType(AttitudeCriterionType.SHARED_CORE_VALUE);configuration.getCriteria().add(additional);
        assertAttitudeBlocksClosure();
    }
    @ParameterizedTest @EnumSource(value=AttitudeEvaluationFormat.class,names={"MANAGER","SALES"})
    void reviewedAttitudeMustMatchTheBoundRoleFormat(AttitudeEvaluationFormat format) {
        period.getAttitudeConfiguration().getRoleMappings().get(0).setEvaluationFormat(format);
        assertAttitudeBlocksClosure();
    }
    @Test void reviewedAttitudeMissingASelfPointCannotClose() {
        attitude.getItems().get(0).setSelfPoint(null);
        assertAttitudeBlocksClosure();
    }
    void assertAttitudeBlocksClosure() {
        var state=service.readiness(1L,actor);assertThat(state.canClose()).isFalse();assertThat(state.completedEmployees()).isZero();
        assertThat(state.attitudeEvaluations().completed()).isZero();assertThat(state.blockers()).anyMatch(b->b.code()==Code.ATTITUDE_SCORE);
        assertThatThrownBy(()->service.close(1L,actor)).isInstanceOf(ReviewPeriodClosureBlockedException.class);
        assertThat(period.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.OPEN);assertThat(attitude.getAttitudeScore()).isEqualByComparingTo("80");
        verify(periods,never()).saveAndFlush(any());verify(attitudes,never()).saveAndFlush(any());
    }
    @Test void oneUnavailableSourceDoesNotEraseAnotherCompletedCheckpointCount() {
        period.setEndDate(LocalDate.of(2027,6,30));participant.setReviewFrequency(ReviewFrequency.QUARTERLY);
        checkpoint.setStartDate(LocalDate.of(2027,4,1));checkpoint.setEndDate(period.getEndDate());checkpoint.setSequenceNumber(2);checkpoint.setReviewFrequency(ReviewFrequency.QUARTERLY);
        assessment.setReviewFrequency(ReviewFrequency.QUARTERLY);
        var earlier=new ReviewCheckpoint();earlier.setId(60L);earlier.setReviewPeriod(period);earlier.setReviewFrequency(ReviewFrequency.QUARTERLY);
        earlier.setStartDate(period.getStartDate());earlier.setEndDate(LocalDate.of(2027,3,31));earlier.setSequenceNumber(1);
        var unavailable=new KpiAssessment();unavailable.setId(61L);unavailable.setParticipant(participant);unavailable.setReviewPeriodId(1L);
        unavailable.setCheckpoint(earlier);unavailable.setReviewFrequency(ReviewFrequency.QUARTERLY);unavailable.setStatus(KpiAssessmentStatus.REVIEWED);
        unavailable.setCheckpointScore(new BigDecimal("80"));
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L,ReviewFrequency.QUARTERLY)).thenReturn(List.of(earlier,checkpoint));
        when(kpis.findPerformanceAssessments(List.of(2L))).thenReturn(List.of(unavailable,assessment));
        when(readiness.evaluate(participant)).thenThrow(new BadRequestException("One recorded checkpoint source is unavailable"));
        when(readiness.checkpointBreakdown(unavailable)).thenThrow(new BadRequestException("Earlier checkpoint source is unavailable"));
        when(readiness.checkpointBreakdown(assessment)).thenReturn(snapshot.checkpoints().get(0).breakdown());
        var state=service.readiness(1L,actor);assertThat(state.canClose()).isFalse();assertThat(state.completedEmployees()).isZero();
        assertThat(state.kpiReviews().required()).isEqualTo(2);assertThat(state.kpiReviews().completed()).isEqualTo(1);
        assertThat(state.attitudeEvaluations().completed()).isEqualTo(1);
        assertThat(state.blockers()).anyMatch(b->Objects.equals(b.checkpointId(),60L) && b.code()==Code.CONFIGURATION)
                .noneMatch(b->Objects.equals(b.checkpointId(),checkpoint.getId()));
        assertThat(assessment.getCheckpointScore()).isEqualByComparingTo("80");assertThat(unavailable.getCheckpointScore()).isEqualByComparingTo("80");
        verify(readiness).checkpointBreakdown(unavailable);verify(readiness).checkpointBreakdown(assessment);
        verify(periods,never()).saveAndFlush(any());verify(kpis,never()).saveAndFlush(any());
    }
    @Test void invalidPeriodWeightsMissingConfigurationAndUnavailableSourcesBlockWithoutRepairs() {
        period.setKpiPerformanceWeight(new BigDecimal("50.001"));period.setAttitudeEvaluationWeight(new BigDecimal("49.999"));
        assertThat(codes()).contains(Code.CONFIGURATION);
        period.setKpiPerformanceWeight(new BigDecimal("50"));period.setAttitudeEvaluationWeight(new BigDecimal("50"));
        when(readiness.evaluate(participant)).thenThrow(new BadRequestException("Recorded source missing"));assertThat(codes()).contains(Code.CONFIGURATION);
        period.setAttitudeConfiguration(null);assertThat(codes()).contains(Code.CONFIGURATION,Code.ATTITUDE_SCORE);
    }
    @Test void invalidKpiItemWeightsOrSuperiorPointsAreConfigurationBlockers() {
        snapshot.checkpoints().get(0).breakdown().levels().get(0).items().get(0).kpi().setWeightage(new BigDecimal("99"));
        assertThat(codes()).contains(Code.CONFIGURATION);
        snapshot.checkpoints().get(0).breakdown().levels().get(0).items().get(0).kpi().setWeightage(new BigDecimal("100"));
        attitude.getItems().get(0).setSuperiorPoint(null);assertThat(codes()).contains(Code.ATTITUDE_SCORE);
    }
    @Test void publicationRosterCannotBeReplacedByCurrentActiveStaffOrLateJoiners() {
        participant.getStaff().setAccountStatus(StaffAccountStatus.INACTIVE);assertThat(service.readiness(1L,actor).canClose()).isTrue();
        assessment.setStatus(KpiAssessmentStatus.DRAFT);assertThat(codes()).contains(Code.KPI_SELF_ASSESSMENT,Code.HR_RECONCILIATION);
        verify(staff,never()).findEligibleReviewStaff(any(),any());
    }
    @Test void emptyOrMissingSnapshotAndMissingSchedulesFailClosed() {
        period.setParticipantsSnapshottedAt(null);assertThat(codes()).contains(Code.MISSING_ROSTER);
        when(participants.findAllByReviewPeriodId(1L)).thenReturn(List.of());assertThat(codes()).contains(Code.EMPTY_ROSTER);
        when(participants.findAllByReviewPeriodId(1L)).thenReturn(List.of(participant));
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(anyLong(),any())).thenReturn(List.of());assertThat(codes()).contains(Code.CONFIGURATION);
    }
    @Test void revokedOrInactiveAdministratorsCannotReadOrCloseEvenWithAnOldToken() {
        when(permissions.existsByRoleIdAndAuthorityName(anyLong(),any())).thenReturn(false);
        assertThatThrownBy(()->service.readiness(1L,actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.close(1L,actor)).isInstanceOf(AccessDeniedException.class);
        verify(periods,never()).saveAndFlush(any());
    }
    @Test void closeRevalidatesInsteadOfTrustingAnEarlierReadinessResponse() {
        assertThat(service.readiness(1L,actor).canClose()).isTrue();assessment.setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        assertThatThrownBy(()->service.close(1L,actor)).isInstanceOf(ReviewPeriodClosureBlockedException.class).satisfies(e->assertThat(((ReviewPeriodClosureBlockedException)e).readiness().blockers()).anyMatch(b->b.code()==Code.KPI_SUPERIOR_REVIEW));
        assertThat(period.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.OPEN);assertThat(period.getClosedAt()).isNull();verify(periods,never()).saveAndFlush(any());
    }
}
