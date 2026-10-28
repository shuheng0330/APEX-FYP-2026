package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffPerformanceServiceTest {
    final ReviewPeriodParticipantRepository participants = mock(ReviewPeriodParticipantRepository.class);
    final StaffRepository staff = mock(StaffRepository.class);
    final KpiAssessmentRepository assessments = mock(KpiAssessmentRepository.class);
    final ReviewCheckpointRepository checkpoints = mock(ReviewCheckpointRepository.class);
    final AttitudeAssessmentRepository attitudes = mock(AttitudeAssessmentRepository.class);
    final AnnualAppraisalRepository appraisals = mock(AnnualAppraisalRepository.class);
    final KpiCheckpointAggregateService aggregates = mock(KpiCheckpointAggregateService.class);
    final PerformanceDepartmentResolver departments = mock(PerformanceDepartmentResolver.class);
    final KpiPlanMapper kpis = Mappers.getMapper(KpiPlanMapper.class);
    final AnnualAppraisalReadinessService readiness = new AnnualAppraisalReadinessService(checkpoints, assessments, attitudes, aggregates, kpis);
    final StaffPerformanceAccess access = new StaffPerformanceAccess(staff, departments);
    final StaffPerformanceService service = new StaffPerformanceService(participants, staff, assessments, checkpoints,
            attitudes, appraisals, readiness, aggregates, access, Mappers.getMapper(StaffPerformanceMapper.class), kpis);
    final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    Staff employee, manager, stranger;
    OrgChart department;
    ReviewPeriodParticipant participant;
    List<ReviewPeriodParticipant> enrolled;
    List<KpiAssessment> records;
    AttitudeAssessment attitude;
    List<AnnualAppraisal> appraisalRecords;

    @BeforeEach void setup() {
        employee = person("Bob"); manager = person("Alice"); stranger = person("Other viewer");
        employee.setManager(manager);
        department = new OrgChart(); department.setId(10L); department.setName("IT");
        when(departments.resolve(any())).thenReturn(department);
        enrolled = new ArrayList<>(); records = new ArrayList<>(); appraisalRecords = new ArrayList<>();
        participant = enrol(1L, 11L, 2026);
        participant.setStaffName("Bob"); participant.setRoleName("Engineer"); participant.setDepartmentName("IT");
        var configuration = new AttitudeConfiguration(); configuration.setId(20L); configuration.setName("Annual values");
        attitude = new AttitudeAssessment(); attitude.setId(21L); attitude.setParticipant(participant);
        attitude.setConfiguration(configuration); attitude.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);
        attitude.setStatus(AttitudeAssessmentStatus.REVIEWED); attitude.setAttitudeScore(decimal("80"));
        var criterion = new AttitudeCriterion(); criterion.setId(22L); criterion.setName("Integrity");
        var answer = new AttitudeAssessmentItem(); answer.setId(23L); answer.setCriterion(criterion);
        answer.setSelfPoint(3); answer.setSelfComment("Self reflection"); answer.setSuperiorPoint(4); answer.setSuperiorComment("Employee feedback");
        attitude.getItems().add(answer);
        when(attitudes.findByParticipantId(11L)).thenAnswer(i -> Optional.ofNullable(attitude));
        when(appraisals.findAllByEmployeeIdOrderBySubmittedAtDescIdDesc(employee.getId())).thenAnswer(i -> appraisalRecords);
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employee.getId())).thenAnswer(i -> enrolled);
        results(ReviewFrequency.QUARTERLY, "60", "80");
        when(aggregates.pendingLevels(any())).thenReturn(List.of());
        when(aggregates.breakdown(any())).thenAnswer(i -> {
            KpiAssessment a = i.getArgument(0);
            return new KpiScoreBreakdownDto(a.getId(), a.getCheckpointScore(), List.of());
        });
        login(employee, "ROLE_USER");
    }

    @AfterEach void readsOnly() {
        for (Object repository : List.of(participants, staff, assessments, checkpoints, attitudes, appraisals)) {
            for (var call : mockingDetails(repository).getInvocations())
                assertTrue(call.getMethod().getName().startsWith("find"), call.toString());
        }
        for (var call : mockingDetails(aggregates).getInvocations())
            assertTrue(List.of("breakdown", "pendingLevels").contains(call.getMethod().getName()), call.toString());
        SecurityContextHolder.clearContext();
    }

    @Test void selfCanReadCompletedScoresAndCannotCreateOrEditAnythingThroughTheContract() {
        var dto = dashboard(employee);
        assertEquals(decimal("70.0000"), dto.scores().annualKpiScore());
        assertEquals(decimal("75.0000"), dto.scores().finalScore());
        assertEquals(2, dto.checkpoints().size()); assertEquals(102L, dto.defaultCheckpointId());
        assertEquals("IT", dto.employee().departmentName()); assertTrue(dto.capabilities().selfView());
        assertFalse(dto.capabilities().canViewInternalAppraisal());
        assertTrue(StaffPerformanceService.class.getAnnotation(Transactional.class).readOnly());
    }

    @ParameterizedTest @EnumSource(AnnualKpiConsolidationMethod.class)
    void usesTheExistingAnnualConsolidationAndWeights(AnnualKpiConsolidationMethod method) {
        participant.getReviewPeriod().setAnnualKpiConsolidationMethod(method);
        participant.getReviewPeriod().setKpiPerformanceWeight(decimal("70"));
        participant.getReviewPeriod().setAttitudeEvaluationWeight(decimal("30"));
        var scores = dashboard(employee).scores();
        assertEquals(decimal(method == AnnualKpiConsolidationMethod.AVERAGE ? "70.0000" : "80.0000"), scores.annualKpiScore());
        assertEquals(decimal(method == AnnualKpiConsolidationMethod.AVERAGE ? "73.0000" : "80.0000"), scores.finalScore());
    }

    @Test void finalCheckpointDoesNotBypassAnEarlierIncompleteReview() {
        participant.getReviewPeriod().setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        records.get(0).setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        var dto = dashboard(employee);
        assertNull(dto.scores().annualKpiScore()); assertNull(dto.scores().finalScore());
        assertEquals(decimal("80"), dto.scores().attitudeScore());
    }

    @Test void completedKpiRemainsAvailableWhileAttitudeIsPending() {
        attitude.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);
        var dto = dashboard(employee);
        assertEquals(decimal("70.0000"), dto.scores().annualKpiScore());
        assertNull(dto.scores().attitudeScore()); assertNull(dto.scores().finalScore());
        assertNull(dto.attitude().criteria().get(0).superiorPoint()); assertNull(dto.attitude().criteria().get(0).superiorComment());
    }

    @Test void missingCentralResultsAndLegitimateZeroesAreDifferent() {
        when(aggregates.pendingLevels(records.get(0))).thenReturn(List.of(KpiLevel.COMPANY));
        assertNull(dashboard(employee).scores().annualKpiScore());
        when(aggregates.pendingLevels(records.get(0))).thenReturn(List.of());
        records.forEach(a -> a.setCheckpointScore(BigDecimal.ZERO)); attitude.setAttitudeScore(BigDecimal.ZERO);
        assertEquals(decimal("0.0000"), dashboard(employee).scores().finalScore());
        assertEquals(1, dashboard(employee).trend().size());
    }

    @Test void noAssessmentsDoesNotCreateDraftsAndStillReturnsThePublishedSchedule() {
        records.clear(); attitude = null;
        var dto = dashboard(employee);
        assertNull(dto.scores().annualKpiScore()); assertNull(dto.scores().attitudeScore()); assertNull(dto.scores().finalScore());
        assertEquals(2, dto.checkpoints().size()); assertNull(dto.checkpoints().get(0).assessmentId());
        assertTrue(dto.trend().isEmpty()); assertTrue(dto.attitude().criteria().isEmpty());
    }

    @Test void unstartedCheckpointAndMissingAttitudeAreReadOnlyEmptyDetails() {
        records.clear(); attitude = null;
        var checkpoint = service.checkpoint(11L, 101L, employee.getId());
        assertNull(checkpoint.checkpoint().assessmentId()); assertNull(checkpoint.breakdown()); assertTrue(checkpoint.answers().isEmpty());
        assertTrue(service.attitude(11L, employee.getId()).criteria().isEmpty());
    }

    @Test void malformedKpiSourceDoesNotHideIndependentlyCompletedAttitude() {
        when(aggregates.pendingLevels(any())).thenThrow(new BadRequestException("Missing recorded allocation"));
        var dto = dashboard(employee);
        assertNull(dto.scores().annualKpiScore()); assertEquals(decimal("80"), dto.scores().attitudeScore());
        assertNull(dto.scores().finalScore());
    }

    @Test void firstTimePendingAndReturnedAppraisalsAreNotEmployeeVisible() {
        for (var status : List.of(AppraisalStatus.DRAFT, AppraisalStatus.PENDING_REVIEW, AppraisalStatus.RETURNED)) {
            var a = appraisal(status); appraisalRecords.clear(); appraisalRecords.add(a);
            assertNull(dashboard(employee).approvedAppraisal()); assertTrue(dashboard(employee).appraisalHistory().isEmpty());
            assertThrows(AccessDeniedException.class, () -> service.appraisal(11L, a.getId(), employee.getId()));
        }
    }

    @Test void employeeApprovedOutcomeOmitsInternalFieldsEvenWhenEmployeeHasHrPermission() throws Exception {
        var a = appraisal(AppraisalStatus.APPROVED); appraisalRecords.add(a);
        login(employee, "ROLE_USER", "CAN_REVIEW_ANNUAL_APPRAISAL", StaffPerformanceAccess.ORGANISATION);
        var dto = service.appraisal(11L, a.getId(), employee.getId());
        assertEquals(AnnualAppraisalRecommendation.PROMOTION, dto.finalDecision()); assertEquals(a.getReviewedAt(), dto.approvedAt());
        assertNull(dto.internalDetails());
        var tree = json.valueToTree(dto);
        assertFalse(tree.has("internalDetails"));
        String body = tree.toString();
        for (String hidden : List.of("Private justification", "Private override", "Private return", "recommendation", "recordedPerformance"))
            assertFalse(body.contains(hidden), hidden);
    }

    @Test void approvedDecisionAndSnapshotStayRecordedAfterPerformanceCorrection() throws Exception {
        var a = appraisal(AppraisalStatus.APPROVED); appraisalRecords.add(a);
        String before = json.writeValueAsString(a.getSnapshot());
        records.get(1).setCheckpointScore(decimal("100"));
        var dto = dashboard(employee);
        assertEquals(decimal("80.0000"), dto.scores().annualKpiScore());
        assertEquals(decimal("80.0000"), dto.scores().finalScore());
        assertEquals(decimal("75.0000"), dto.approvedAppraisal().recordedScores().finalScore());
        assertEquals(StaffPerformanceDto.RecordedComparison.DIFFERENT, dto.approvedAppraisal().comparisonStatus());
        assertEquals(AnnualAppraisalRecommendation.PROMOTION, dto.approvedAppraisal().finalDecision());
        assertEquals(before, json.writeValueAsString(a.getSnapshot()));
        assertEquals(AppraisalStatus.APPROVED, a.getStatus());
        assertEquals(AnnualAppraisalRecommendation.SALARY_INCREMENT, a.getRecommendation());
        assertEquals("Private justification", a.getJustification());
    }

    @Test void unchangedNumericFormattingAndUnavailableCurrentResultsDoNotAlterApproval() {
        var a = appraisal(AppraisalStatus.APPROVED); appraisalRecords.add(a);
        assertEquals(StaffPerformanceDto.RecordedComparison.UNCHANGED, dashboard(employee).approvedAppraisal().comparisonStatus());
        records.get(0).setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        assertEquals(StaffPerformanceDto.RecordedComparison.CURRENT_RESULTS_UNAVAILABLE, dashboard(employee).approvedAppraisal().comparisonStatus());
        assertNotNull(dashboard(employee).approvedAppraisal().recordedScores());
    }

    @Test void viewingPermissionsDoNotGrantInternalAppraisalAccess() {
        var a = appraisal(AppraisalStatus.APPROVED); appraisalRecords.add(a);
        login(manager, StaffPerformanceAccess.TEAM);
        var dto = dashboard(manager);
        assertNull(dto.approvedAppraisal()); assertFalse(dto.capabilities().canViewInternalAppraisal());
        assertThrows(AccessDeniedException.class, () -> service.appraisal(11L, a.getId(), manager.getId()));
        login(manager, StaffPerformanceAccess.TEAM, "CAN_SUBMIT_ANNUAL_APPRAISAL");
        assertEquals("Private justification", dashboard(manager).approvedAppraisal().internalDetails().justification());
        login(stranger, StaffPerformanceAccess.ORGANISATION, "CAN_REVIEW_ANNUAL_APPRAISAL");
        assertEquals("Private override", dashboard(stranger).approvedAppraisal().internalDetails().overrideReason());
    }

    @Test void roleNamesAndExistingWritePermissionsDoNotGrantPerformanceScope() {
        for (String authority : List.of("ROLE_USER", "ROLE_SUPER_ADMIN", "CAN_MANAGE_EVALUATION", "CAN_VIEW_STAFF",
                "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ATTITUDE_EVALUATION", "CAN_REVIEW_ANNUAL_APPRAISAL", "CAN_SUBMIT_ANNUAL_APPRAISAL")) {
            login(manager, authority);
            assertThrows(AccessDeniedException.class, () -> dashboard(manager), authority);
            assertThrows(AccessDeniedException.class, () -> service.attitude(11L, manager.getId()), authority);
        }
    }

    @Test void directReportAccessUsesTheCurrentReportingRelationship() {
        login(manager, StaffPerformanceAccess.TEAM); assertEquals(1, service.periods(employee.getId(), manager.getId()).size());
        employee.setManager(stranger);
        assertThrows(AccessDeniedException.class, () -> dashboard(manager));
        assertThrows(AccessDeniedException.class, () -> service.checkpoint(11L, 101L, manager.getId()));
    }

    @Test void departmentAccessUsesRecordedMembershipAndCurrentAuthorisedDepartment() {
        login(stranger, StaffPerformanceAccess.DEPARTMENT);
        assertEquals(1, service.periods(employee.getId(), stranger.getId()).size());
        var other = new OrgChart(); other.setId(99L); participant.setDepartment(other);
        assertTrue(service.periods(employee.getId(), stranger.getId()).isEmpty());
        assertThrows(AccessDeniedException.class, () -> dashboard(stranger));
        assertThrows(AccessDeniedException.class, () -> service.attitude(11L, stranger.getId()));
        login(stranger, StaffPerformanceAccess.ORGANISATION);
        assertEquals(1, service.periods(employee.getId(), stranger.getId()).size());
    }

    @Test void missingDepartmentCannotBroadenAccessAndInactiveActorsCannotRead() {
        login(stranger, StaffPerformanceAccess.DEPARTMENT); when(departments.resolve(any())).thenReturn(null);
        assertTrue(service.periods(employee.getId(), stranger.getId()).isEmpty());
        stranger.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class, () -> service.periods(employee.getId(), stranger.getId()));
    }

    @Test void spoofedActorMissingParticipantAndMismatchedCheckpointAreDenied() {
        assertThrows(AccessDeniedException.class, () -> service.periods(employee.getId(), manager.getId()));
        assertThrows(AccessDeniedException.class, () -> service.attitude(999L, employee.getId()));
        assertThrows(AccessDeniedException.class, () -> service.checkpoint(11L, 999L, employee.getId()));
        var a = appraisal(AppraisalStatus.APPROVED); a.getParticipant().setId(999L);
        assertThrows(AccessDeniedException.class, () -> service.appraisal(11L, a.getId(), employee.getId()));
    }

    @Test void draftPeriodIsHiddenAndUpcomingShowsScheduleWithoutInventedScores() {
        participant.getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.DRAFT);
        assertTrue(service.periods(employee.getId(), employee.getId()).isEmpty());
        assertThrows(AccessDeniedException.class, () -> dashboard(employee));
        participant.getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);
        assertNull(dashboard(employee).scores().finalScore()); assertTrue(dashboard(employee).trend().isEmpty());
    }

    @ParameterizedTest @EnumSource(KpiAssessmentModel.class)
    void checkpointDetailsPreserveSupportedAssessmentModelsAndHideSuperiorDrafts(KpiAssessmentModel model) {
        var a = records.get(0); a.setAssessmentModel(model);
        var plan = new KpiPlan(); plan.setLevel(KpiLevel.INDIVIDUAL);
        var kpi = new Kpi(); kpi.setId(500L); kpi.setName("Support resolution"); kpi.setPlan(plan);
        kpi.setTarget("Resolve 90% within SLA"); kpi.setWeightage(decimal("100"));
        var assignment = new EmployeeKpiAssignment(); assignment.setKpi(kpi);
        var item = new KpiAssessmentItem(); item.setId(501L); item.setAssignment(assignment);
        item.setSelfPoint(3); item.setSelfComment("Submitted self comment"); item.setSuperiorPoint(5); item.setSuperiorComment("Unsaved/private Superior draft");
        a.getItems().add(item); a.setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        var pending = service.checkpoint(11L, a.getCheckpoint().getId(), employee.getId());
        assertEquals(3, pending.answers().get(0).selfPoint()); assertNull(pending.answers().get(0).superiorPoint());
        assertNull(pending.answers().get(0).superiorComment()); assertNull(pending.checkpoint().score());
        a.setStatus(KpiAssessmentStatus.REVIEWED);
        assertEquals(5, service.checkpoint(11L, a.getCheckpoint().getId(), employee.getId()).answers().get(0).superiorPoint());
    }

    @Test void anotherViewerCannotReadUnsubmittedEmployeeDraftAnswers() {
        records.get(0).setStatus(KpiAssessmentStatus.DRAFT); attitude.setStatus(AttitudeAssessmentStatus.DRAFT);
        login(manager, StaffPerformanceAccess.TEAM);
        var dto = service.checkpoint(11L, 101L, manager.getId());
        assertNull(dto.checkpoint().assessmentId()); assertTrue(dto.answers().isEmpty()); assertNull(dto.breakdown());
        assertTrue(service.attitude(11L, manager.getId()).criteria().isEmpty());
    }

    @Test void completedAttitudeContainsSelfAndSuperiorFeedbackButNoInternalAppraisalFields() throws Exception {
        var dto = service.attitude(11L, employee.getId());
        assertEquals(3, dto.criteria().get(0).selfPoint()); assertEquals(4, dto.criteria().get(0).superiorPoint());
        assertEquals("Employee feedback", dto.criteria().get(0).superiorComment());
        assertFalse(json.writeValueAsString(dto).contains("overrideReason"));
    }

    @Test void historyRetainsSameYearPeriodsUsesActualDatesAndLatestScoresWithoutSnapshots() {
        var older = enrol(2L, 12L, 2024);
        var sameYear = enrol(3L, 13L, 2024); sameYear.getReviewPeriod().setName("[UAT SIMULATED] Second 2024 period");
        sameYear.getReviewPeriod().setEndDate(LocalDate.of(2024, 6, 30));
        var future = enrol(4L, 14L, 2027);
        var excluded = enrol(5L, 15L, 2020);
        // These periods have independently completed Attitude results but no annual KPI yet.
        for (var p : List.of(older, sameYear, future, excluded)) {
            var a = new AttitudeAssessment(); a.setConfiguration(attitude.getConfiguration());
            a.setStatus(AttitudeAssessmentStatus.REVIEWED); a.setAttitudeScore(decimal("65"));
            when(attitudes.findByParticipantId(p.getId())).thenReturn(Optional.of(a));
        }
        var trend = dashboard(employee).trend();
        assertEquals(List.of(3L, 2L, 1L), trend.stream().map(t -> t.period().id()).toList());
        assertEquals("[UAT SIMULATED] Second 2024 period", trend.get(0).period().name());
        assertTrue(trend.stream().allMatch(t -> t.sourceType() == StaffPerformanceDto.SourceType.LATEST_COMPLETED_RESULTS));
        assertThrows(BadRequestException.class, () -> service.dashboard(employee.getId(), 1L, 4, employee.getId()));
    }

    @Test void historicalApprovedOutcomesAreAvailableWithoutLeakingInternalText() {
        var old = enrol(2L, 12L, 2024);
        var a = appraisal(AppraisalStatus.APPROVED); a.setParticipant(old); a.setReviewPeriodId(2L); appraisalRecords.add(a);
        var history = dashboard(employee).appraisalHistory();
        assertEquals(1, history.size()); assertEquals(2L, history.get(0).period().id());
        assertEquals(AnnualAppraisalRecommendation.PROMOTION, history.get(0).finalDecision());
        assertNull(history.get(0).internalDetails());
    }

    private Staff person(String name) {
        var s = new Staff(); s.setId(UUID.randomUUID()); s.setName(name); s.setAccountStatus(StaffAccountStatus.ACTIVE);
        var role = new Role(); role.setId(1L); s.setRole(role);
        when(staff.findById(s.getId())).thenReturn(Optional.of(s)); return s;
    }
    private ReviewPeriodParticipant enrol(Long periodId, Long id, int year) {
        var period = new AnnualKpiReviewPeriod(); period.setId(periodId); period.setName(year + " Annual KPI Review");
        period.setStartDate(LocalDate.of(year, 1, 1)); period.setEndDate(LocalDate.of(year, 12, 31));
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN); period.setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);
        period.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS); period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.AVERAGE);
        period.setKpiPerformanceWeight(decimal("50")); period.setAttitudeEvaluationWeight(decimal("50"));
        var p = new ReviewPeriodParticipant(); p.setId(id); p.setStaff(employee); p.setReviewPeriod(period);
        p.setDepartment(department); p.setReviewFrequency(ReviewFrequency.QUARTERLY); enrolled.add(p);
        when(participants.findById(id)).thenReturn(Optional.of(p)); return p;
    }
    private void results(ReviewFrequency frequency, String... scores) {
        participant.setReviewFrequency(frequency); records.clear(); var schedule = new ArrayList<ReviewCheckpoint>();
        for (int i = 0; i < scores.length; i++) {
            var c = new ReviewCheckpoint(); c.setId(101L + i); c.setSequenceNumber(i + 1); c.setReviewFrequency(frequency);
            c.setReviewPeriod(participant.getReviewPeriod()); c.setStartDate(LocalDate.of(2026, 1, 1).plusMonths(i * 3L));
            c.setEndDate(c.getStartDate().plusMonths(3).minusDays(1)); schedule.add(c);
            var a = new KpiAssessment(); a.setId(201L + i); a.setParticipant(participant); a.setCheckpoint(c);
            a.setStatus(KpiAssessmentStatus.REVIEWED); a.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
            a.setCheckpointScore(decimal(scores[i])); records.add(a);
        }
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L, frequency)).thenReturn(schedule);
        when(assessments.findAllByParticipantId(11L)).thenAnswer(i -> records);
    }
    private AnnualAppraisal appraisal(AppraisalStatus status) {
        var a = new AnnualAppraisal(); a.setId(30L); a.setParticipant(participant); a.setReviewPeriodId(1L); a.setEmployeeId(employee.getId());
        a.setStatus(status); a.setFinalDecision(AnnualAppraisalRecommendation.PROMOTION);
        a.setRecommendation(AnnualAppraisalRecommendation.SALARY_INCREMENT);
        a.setJustification("Private justification"); a.setOverrideReason("Private override"); a.setLatestReturnReason("Private return");
        a.setReviewedAt(OffsetDateTime.parse("2026-10-29T09:00:00+08:00")); a.setSnapshot(readiness.evaluate(participant).performance());
        when(appraisals.findById(a.getId())).thenReturn(Optional.of(a)); return a;
    }
    private void login(Staff actor, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getId(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
    private StaffPerformanceDto dashboard(Staff actor) { return service.dashboard(employee.getId(), 1L, 5, actor.getId()); }
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
