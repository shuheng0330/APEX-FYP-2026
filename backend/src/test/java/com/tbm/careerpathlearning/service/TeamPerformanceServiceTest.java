package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.mapper.TeamPerformanceMapper;
import org.mapstruct.factory.Mappers;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.tbm.careerpathlearning.service.StaffPerformanceAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamPerformanceServiceTest {
    final TeamPerformanceRepository repository = mock(TeamPerformanceRepository.class);
    final ReviewCheckpointRepository checkpointRepository = mock(ReviewCheckpointRepository.class);
    final KpiAssessmentRepository kpiRepository = mock(KpiAssessmentRepository.class);
    final AttitudeAssessmentRepository attitudeRepository = mock(AttitudeAssessmentRepository.class);
    final StaffRepository staff = mock(StaffRepository.class);
    final PerformanceDepartmentResolver departments = mock(PerformanceDepartmentResolver.class);
    final RoleAuthorityRepository roleAuthorities = mock(RoleAuthorityRepository.class);
    final StaffPerformanceAccess access = new StaffPerformanceAccess(staff, departments);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-29T02:00:00Z"), ZoneId.of("Asia/Singapore"));
    final TeamPerformanceService service = new TeamPerformanceService(repository,
            new TeamPerformanceResults(repository, checkpointRepository, kpiRepository, attitudeRepository), access, departments, roleAuthorities,
            Mappers.getMapper(TeamPerformanceMapper.class), clock);
    final List<AnnualKpiReviewPeriod> periods = new ArrayList<>();
    final List<ReviewPeriodParticipant> population = new ArrayList<>();
    final List<ReviewCheckpoint> checkpoints = new ArrayList<>();
    final List<KpiAssessment> assessments = new ArrayList<>();
    final List<AttitudeAssessment> attitudes = new ArrayList<>();
    final List<TeamPerformanceRepository.VersionSource> fallback = new ArrayList<>();
    final List<TeamPerformanceRepository.PublishedSource> published = new ArrayList<>();
    final List<TeamPerformanceRepository.IndividualPlan> plans = new ArrayList<>();
    final List<TeamPerformanceRepository.ReturnedRevision> revisions = new ArrayList<>();
    Staff actor, bob;
    OrgChart department;
    ReviewPeriodParticipant current;
    long nextId = 100;

    @BeforeEach void setup() {
        actor = person("Alice"); bob = person("Bob"); bob.setManager(actor);
        var role = new Role(); role.setId(2L); actor.setRole(role);
        when(roleAuthorities.existsByRoleIdAndAuthorityName(2L, AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI)).thenReturn(true);
        department = new OrgChart(); department.setId(10L); department.setName("IT");
        when(departments.resolve(any())).thenReturn(department);
        var departmentOptions = List.of(department(10L, "IT"), department(20L, "Finance"));
        when(repository.findDepartments()).thenReturn(departmentOptions);
        when(repository.findPeriods(anyBoolean(), any(), any())).thenAnswer(i -> periods.stream()
                .filter(period -> population.stream().anyMatch(p -> p.getReviewPeriod() == period && inScope(p, i.getArgument(0), i.getArgument(1), i.getArgument(2))))
                .toList());
        when(repository.findPopulation(anyBoolean(), any(), any(), anyCollection())).thenAnswer(i -> population.stream()
                .filter(p -> ((Collection<?>) i.getArgument(3)).contains(p.getReviewPeriod().getId())
                        && inScope(p, i.getArgument(0), i.getArgument(1), i.getArgument(2))).toList());
        when(checkpointRepository.findPerformanceCheckpoints(anyCollection())).thenAnswer(i -> checkpoints.stream()
                .filter(c -> ((Collection<?>) i.getArgument(0)).contains(c.getReviewPeriod().getId())).toList());
        when(kpiRepository.findPerformanceAssessments(anyCollection())).thenAnswer(i -> assessments.stream()
                .filter(a -> ((Collection<?>) i.getArgument(0)).contains(a.getParticipant().getId())).toList());
        when(attitudeRepository.findPerformanceAssessments(anyCollection())).thenAnswer(i -> attitudes.stream()
                .filter(a -> ((Collection<?>) i.getArgument(0)).contains(a.getParticipant().getId())).toList());
        when(repository.findFallbackVersions(anyCollection())).thenAnswer(i -> fallback);
        when(repository.findPublishedSources(anyCollection())).thenAnswer(i -> published);
        when(repository.findIndividualPlans(anyCollection())).thenAnswer(i -> plans);
        when(repository.findReturnedRevisions(anyCollection())).thenAnswer(i -> revisions);
        current = enrol(bob, period(1L, 2026), "60", "80", "80");
        login(TEAM);
    }

    @AfterEach void readOnly() {
        for (var read : List.of(repository, checkpointRepository, kpiRepository, attitudeRepository))
            for (var call : mockingDetails(read).getInvocations()) assertTrue(call.getMethod().getName().startsWith("find"), call.toString());
        SecurityContextHolder.clearContext();
    }

    @Test void completedScoresHaveIndependentContributorsAndEqualEmployeeWeight() {
        var second = enrol(personReportingToActor("Zoe"), current.getReviewPeriod(), "100", "100", "60");
        var result = dashboard();
        assertEquals(2, result.summary().totalEmployees()); assertEquals(2, result.summary().employeesWithResults());
        assertEquals(n("85.0000"), result.summary().kpi().score()); assertEquals(n("70.0000"), result.summary().attitude().score());
        assertEquals(n("77.5000"), result.summary().finalScore().score());
        assertEquals(second.getId(), result.employees().get(1).participantId());
        assertTrue(TeamPerformanceService.class.getAnnotation(Transactional.class).readOnly());
    }

    @ParameterizedTest @EnumSource(AnnualKpiConsolidationMethod.class)
    void bothConsolidationMethodsRequireEveryCheckpoint(AnnualKpiConsolidationMethod method) {
        current.getReviewPeriod().setAnnualKpiConsolidationMethod(method);
        assertEquals(n(method == AnnualKpiConsolidationMethod.AVERAGE ? "70.0000" : "80.0000"), dashboard().summary().kpi().score());
        assessments.get(0).setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        var result = dashboard();
        assertNull(result.summary().kpi().score()); assertEquals(0, result.summary().kpi().contributingEmployees());
        assertEquals(n("80.0000"), result.summary().attitude().score()); assertNull(result.summary().finalScore().score());
    }

    @Test void validZeroesRemainResultsAndZeroWeightsDoNotWaiveCompletion() {
        assessments.forEach(a -> a.setCheckpointScore(BigDecimal.ZERO)); attitudes.get(0).setAttitudeScore(BigDecimal.ZERO);
        assertEquals(n("0.0000"), dashboard().summary().finalScore().score());
        assertEquals(1, dashboard().distribution().bins().get(0).employees());
        current.getReviewPeriod().setKpiPerformanceWeight(BigDecimal.ZERO); current.getReviewPeriod().setAttitudeEvaluationWeight(n("100"));
        assessments.remove(0);
        assertNull(dashboard().summary().finalScore().score());
    }

    @Test void invalidScoresAndWeightsCannotEnterAverages() {
        assessments.get(0).setCheckpointScore(n("100.0001"));
        attitudes.get(0).setAttitudeScore(n("-1"));
        assertNull(dashboard().summary().kpi().score()); assertNull(dashboard().summary().attitude().score());
        assessments.get(0).setCheckpointScore(n("60")); attitudes.get(0).setAttitudeScore(n("80"));
        current.getReviewPeriod().setAttitudeEvaluationWeight(n("49.99"));
        assertNotNull(dashboard().summary().kpi().score()); assertNull(dashboard().summary().finalScore().score());
    }

    @Test void incompleteEmployeesDoNotDiluteCompletedAverages() {
        var p = enrol(personReportingToActor("Incomplete"), current.getReviewPeriod(), "0", "0", "40");
        assessments.removeIf(a -> a.getParticipant() == p);
        var result = dashboard();
        assertEquals(2, result.summary().totalEmployees()); assertEquals(1, result.summary().employeesWithResults());
        assertEquals(n("70.0000"), result.summary().kpi().score()); assertEquals(1, result.summary().kpi().contributingEmployees());
        assertEquals(n("60.0000"), result.summary().attitude().score()); assertEquals(2, result.summary().attitude().contributingEmployees());
        assertEquals(1, result.distribution().missingResults());
    }

    @Test void emptyTeamHasNullAveragesAndZeroCounts() {
        bob.setManager(person("New Manager"));
        assertTrue(service.periods(Scope.TEAM, null, actor.getId()).isEmpty());
        // A period may become empty between the authorised period query and the population query.
        when(repository.findPeriods(true, actor.getId(), null)).thenReturn(List.of(current.getReviewPeriod()));
        var result = dashboard();
        assertEquals(0, result.summary().totalEmployees()); assertNull(result.summary().finalScore().score());
        assertTrue(result.employees().isEmpty()); assertEquals(0, result.distribution().missingResults());
    }

    @Test void distributionsUseNeutralNumericBoundariesAndExcludeMissing() {
        population.clear(); assessments.clear(); attitudes.clear();
        for (String score : List.of("0", "59.9999", "60", "69.9999", "70", "79.9999", "80", "89.9999", "90", "100"))
            enrol(personReportingToActor(score), current.getReviewPeriod(), score, score, score);
        var missing = enrol(personReportingToActor("Pending"), current.getReviewPeriod(), "80", "80", "80");
        attitudes.removeIf(a -> a.getParticipant() == missing);
        var result = dashboard();
        result.distribution().bins().forEach(b -> assertEquals(2, b.employees()));
        assertEquals(10, result.distribution().employeesWithResults()); assertEquals(1, result.distribution().missingResults());
        assertTrue(result.distribution().bins().get(4).upperInclusive()); assertFalse(result.distribution().bins().get(0).upperInclusive());
        assertEquals(11, service.dashboard(Scope.TEAM, null, 1L, 5, Metric.KPI, actor.getId()).distribution().employeesWithResults());
    }

    @Test void monthlyCompanySourceMustMatchQuarterlyEndAndPreserveBoundVersion() {
        central(current, "15", "15", "70");
        var monthly = monthly(current.getReviewPeriod(), 6);
        publish(11L, monthly.getId()); publish(12L, checkpoints.get(0).getId());
        publish(11L, monthly(current.getReviewPeriod(), 12).getId()); publish(12L, checkpoints.get(1).getId());
        assertEquals(n("70.0000"), dashboard().summary().kpi().score());
        published.removeIf(s -> Objects.equals(s.getCheckpointId(), monthly.getId()));
        publish(11L, monthly(current.getReviewPeriod(), 4).getId()); publish(11L, monthly(current.getReviewPeriod(), 5).getId());
        assertNull(dashboard().summary().kpi().score(), "April/May cannot stand in for June");
        publish(99L, monthly.getId());
        assertNull(dashboard().summary().kpi().score(), "a new plan version cannot replace the started version");
        publish(11L, monthly.getId());
        assertNotNull(dashboard().summary().kpi().score());
    }

    @Test void legacyRolePolicyUsesSameCheckpointAndLegacyAllLevelsNeedNoCentralPublication() {
        assertNotNull(dashboard().summary().kpi().score());
        central(current, "15", "15", "70"); current.getReviewPeriod().setFrequencyPolicy(KpiFrequencyPolicy.ROLE_BASED);
        for (var c : List.copyOf(checkpoints)) { publish(11L, c.getId()); publish(12L, c.getId()); }
        assertNotNull(dashboard().summary().kpi().score());
    }

    @Test void centralAllocationValidationAndZeroAllocationMatchExistingCompletionRules() {
        central(current, "0", "0", "100");
        assertNotNull(dashboard().summary().kpi().score());
        current.getEmployeeLevelConfiguration().setCompanyKpiWeight(n("1"));
        assertNull(dashboard().summary().kpi().score());
        current.getEmployeeLevelConfiguration().setCompanyKpiWeight(n("0.001"));
        current.getEmployeeLevelConfiguration().setIndividualKpiWeight(n("99.999"));
        assertNull(dashboard().summary().kpi().score());
        current.setEmployeeLevelConfiguration(null); assertNull(dashboard().summary().kpi().score());
    }

    @Test void unboundConfirmedAssignmentFallbackIsReadOnlyAndCannotOverrideBinding() {
        central(current, "15", "0", "85");
        monthly(current.getReviewPeriod(), 6); monthly(current.getReviewPeriod(), 12);
        assessments.forEach(a -> a.getPlanVersions().clear());
        fallback.add(version(current, KpiLevel.COMPANY, 11L));
        checkpoints.stream().filter(c -> c.getReviewFrequency() == ReviewFrequency.MONTHLY).forEach(c -> publish(11L, c.getId()));
        assertNotNull(dashboard().summary().kpi().score());
        assertTrue(assessments.get(0).getPlanVersions().isEmpty(), "GET must not bind missing versions");
        bind(assessments.get(0), KpiLevel.COMPANY, 99L);
        assertNull(dashboard().summary().kpi().score());
    }

    @Test void ambiguousOrWrongPeriodFallbackIsUnavailable() {
        central(current, "15", "0", "85"); assessments.forEach(a -> a.getPlanVersions().clear());
        fallback.add(version(current, KpiLevel.COMPANY, 11L)); fallback.add(version(current, KpiLevel.COMPANY, 99L));
        publish(11L, monthly(current.getReviewPeriod(), 6).getId()); publish(11L, monthly(current.getReviewPeriod(), 12).getId());
        assertNull(dashboard().summary().kpi().score());
        fallback.clear(); var wrong = version(current, KpiLevel.COMPANY, 11L); when(wrong.getPeriodId()).thenReturn(999L); fallback.add(wrong);
        assertNull(dashboard().summary().kpi().score());
    }

    @Test void teamHistoryUsesCurrentManagerNotRecordedSuperior() {
        var prior = enrol(bob, period(2L, 2025), "50", "50", "50");
        prior.setSuperior(person("Old manager"));
        assertEquals(2, dashboard().trend().size());
        bob.setManager(person("Other current manager"));
        assertThrows(AccessDeniedException.class, this::dashboard);
    }

    @Test void departmentPermissionUsesRecordedMembershipAndDoesNotGrantActions() {
        bob.setManager(person("Other manager")); assessments.forEach(a -> a.setStatus(KpiAssessmentStatus.PENDING_REVIEW));
        attitudes.get(0).setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);
        login(DEPARTMENT);
        var result = service.dashboard(Scope.DEPARTMENT, 10L, 1L, 5, Metric.FINAL, actor.getId());
        assertEquals(2, result.attention().scopeProgress().kpiSuperiorReviews()); assertEquals(1, result.attention().scopeProgress().attitudeSuperiorReviews());
        assertEquals(0, result.attention().yourActions().pendingKpiReviews()); assertTrue(result.attention().yourActions().links().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.periods(Scope.DEPARTMENT, 20L, actor.getId()));
        assertThrows(AccessDeniedException.class, () -> service.periods(Scope.TEAM, null, actor.getId()));
    }

    @Test void organisationViewerCanDrillRecordedDepartmentsButHasNoImplicitReviewAuthority() {
        login(ORGANISATION);
        assertEquals(2, service.scopes(actor.getId()).size());
        assertEquals(1, service.dashboard(Scope.DEPARTMENT, 10L, 1L, 5, Metric.FINAL, actor.getId()).summary().totalEmployees());
        assertTrue(service.periods(Scope.DEPARTMENT, 20L, actor.getId()).isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.periods(Scope.DEPARTMENT, 999L, actor.getId()));
        assertThrows(AccessDeniedException.class, () -> service.periods(Scope.TEAM, null, actor.getId()));
    }

    @Test void ordinaryEmployeesAndRoleNamesDoNotGrantTeamViewing() {
        for (String permission : List.of("ROLE_USER", "ROLE_SUPER_ADMIN", "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ANNUAL_APPRAISAL")) {
            login(permission); assertThrows(AccessDeniedException.class, this::dashboard);
        }
        verify(repository, never()).findPopulation(anyBoolean(), any(), any(), anyCollection());
    }

    @Test void inactiveDeletedAndForgedActorsAreDenied() {
        actor.setDeleted(true); assertThrows(AccessDeniedException.class, this::dashboard);
        actor.setDeleted(false); actor.setAccountStatus(StaffAccountStatus.INACTIVE); assertThrows(AccessDeniedException.class, this::dashboard);
        actor.setAccountStatus(StaffAccountStatus.ACTIVE);
        assertThrows(AccessDeniedException.class, () -> service.scopes(UUID.randomUUID()));
    }

    @Test void scopeAndPeriodInputsCannotBroadenAccess() {
        assertThrows(AccessDeniedException.class, () -> service.periods(Scope.TEAM, 10L, actor.getId()));
        assertThrows(AccessDeniedException.class, () -> service.dashboard(Scope.TEAM, null, 999L, 5, Metric.FINAL, actor.getId()));
        assertThrows(BadRequestException.class, () -> service.dashboard(Scope.TEAM, null, 1L, 4, Metric.FINAL, actor.getId()));
        assertThrows(BadRequestException.class, () -> service.dashboard(Scope.TEAM, null, -1L, 5, Metric.FINAL, actor.getId()));
        current.getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.DRAFT); assertThrows(AccessDeniedException.class, this::dashboard);
    }

    @Test void publicationSnapshotIsRequiredAndUpcomingScoresStayPending() {
        current.getReviewPeriod().setParticipantsSnapshottedAt(null); assertThrows(AccessDeniedException.class, this::dashboard);
        current.getReviewPeriod().setParticipantsSnapshottedAt(OffsetDateTime.now(clock)); current.getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);
        var result = dashboard(); assertNull(result.summary().finalScore().score()); assertNull(result.summary().kpi().score());
        assertNull(result.summary().attitude().score());
    }

    @Test void latestCompletedCorrectionsAreReadWithoutAnyAppraisalDependency() {
        assertEquals(n("75.0000"), dashboard().summary().finalScore().score());
        assessments.forEach(a -> a.setCheckpointScore(n("90")));
        assertEquals(n("85.0000"), dashboard().summary().finalScore().score());
        assertTrue(Arrays.stream(TeamPerformanceService.class.getDeclaredFields()).noneMatch(f -> f.getType() == AnnualAppraisalRepository.class));
    }

    @Test void sameYearPeriodsRemainSeparateAndDateWindowIsDeterministic() {
        enrol(bob, period(2L, 2025), "10", "10", "10");
        enrol(bob, period(3L, 2025), "20", "20", "20");
        enrol(bob, period(4L, 2022), "30", "30", "30");
        enrol(bob, period(5L, 2021), "40", "40", "40");
        enrol(bob, period(6L, 2027), "50", "50", "50");
        assertEquals(List.of(4L, 2L, 3L, 1L), dashboard().trend().stream().map(t -> t.period().id()).toList());
        assertEquals(List.of(2L, 3L, 1L), service.dashboard(Scope.TEAM, null, 1L, 3, Metric.FINAL, actor.getId()).trend().stream().map(t -> t.period().id()).toList());
        assertNotNull(dashboard().trend().get(0).period().startDate()); assertNotNull(dashboard().trend().get(0).period().name());
        assertEquals(Comparison.AMBIGUOUS_PREVIOUS_PERIOD, dashboard().yearOnYear().status());
    }

    @Test void comparableYearOnYearUsesPercentagePointsAndNumericEquality() {
        var prior = enrol(bob, period(2L, 2025), "50", "50", "70");
        prior.getReviewPeriod().setKpiPerformanceWeight(n("50.0")); prior.getEmployeeLevelConfiguration().setIndividualKpiWeight(n("100.0"));
        var result = dashboard();
        assertEquals(n("15.0000"), result.yearOnYear().percentagePoints()); assertEquals(Comparison.AVAILABLE, result.yearOnYear().status());
        assertEquals(n("15.0000"), result.employees().get(0).yearOnYear().percentagePoints());
    }

    @Test void configurationAndPartialPeriodChangesPreventYoY() {
        var prior = enrol(bob, period(2L, 2025), "50", "50", "70");
        prior.getReviewPeriod().setStartDate(LocalDate.of(2025, 4, 1));
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().yearOnYear().status());
        prior.getReviewPeriod().setStartDate(LocalDate.of(2025, 1, 1)); prior.getReviewPeriod().setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().yearOnYear().status());
        prior.getReviewPeriod().setFrequencyPolicy(KpiFrequencyPolicy.ROLE_BASED); prior.getReviewPeriod().setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().yearOnYear().status());
    }

    @Test void frequencyAllocationAndRecordedScheduleChangesPreventEmployeeComparisons() {
        var prior = enrol(bob, period(2L, 2025), "50", "50", "70");
        prior.getEmployeeLevelConfiguration().setIndividualKpiWeight(n("70")); prior.getEmployeeLevelConfiguration().setCompanyKpiWeight(n("30"));
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().employees().get(0).yearOnYear().status());
        prior.getEmployeeLevelConfiguration().setIndividualKpiWeight(n("100")); prior.getEmployeeLevelConfiguration().setCompanyKpiWeight(n("0"));
        checkpoints.stream().filter(c -> c.getReviewPeriod() == prior.getReviewPeriod()).findFirst().orElseThrow().setEndDate(LocalDate.of(2025, 5, 31));
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().employees().get(0).yearOnYear().status());
    }

    @Test void changedEmployeesAreAllowedForScopeYoYButNewEmployeesHaveNoPersonalComparator() {
        enrol(personReportingToActor("Previous member"), period(2L, 2025), "50", "50", "70");
        var result = dashboard();
        assertEquals(Comparison.AVAILABLE, result.yearOnYear().status());
        assertEquals(Comparison.NO_COMPARABLE_RESULTS, result.employees().get(0).yearOnYear().status());
    }

    @Test void missingPreviousPeriodAndMissingScoresHaveExplicitReasons() {
        assertEquals(Comparison.NO_PREVIOUS_PERIOD, dashboard().yearOnYear().status());
        var prior = enrol(bob, period(2L, 2025), "50", "50", "70");
        attitudes.removeIf(a -> a.getParticipant() == prior);
        assertEquals(Comparison.NO_COMPARABLE_RESULTS, dashboard().yearOnYear().status());
    }

    @Test void missingRecordedAllocationNeverCreatesAnApparentlyComparableYoY() {
        var prior = enrol(bob, period(2L, 2025), "50", "50", "70");
        prior.setEmployeeLevelConfiguration(null); current.setEmployeeLevelConfiguration(null);
        assertNotNull(dashboard().summary().finalScore().score(), "legacy completed scores are still readable");
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().yearOnYear().status());
        assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION, dashboard().employees().get(0).yearOnYear().status());
    }

    @Test void unassignedDepartmentOptionIsNotAnOrganisationWideWildcard() {
        var options = List.of(department(null, null), department(10L, "IT"));
        when(repository.findDepartments()).thenReturn(options); login(ORGANISATION);
        assertEquals("Unassigned Department", service.scopes(actor.getId()).get(0).name());
        assertTrue(service.periods(Scope.DEPARTMENT, null, actor.getId()).isEmpty());
        current.setDepartment(null);
        assertEquals(1, service.dashboard(Scope.DEPARTMENT, null, 1L, 5, Metric.FINAL, actor.getId()).summary().totalEmployees());
    }

    @Test void personalActionsRequireBothRoutingAndRealActionPermissions() {
        pendingReviews(); plans.add(plan(current, KpiPlanStatus.PENDING_APPROVAL, actor.getId()));
        var viewOnly = dashboard(); assertEquals(0, viewOnly.attention().yourActions().pendingKpiReviews());
        login(TEAM, "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ATTITUDE_EVALUATION", "CAN_REVIEW_INDIVIDUAL_KPI");
        var actions = dashboard().attention().yourActions();
        assertEquals(2, actions.pendingKpiReviews()); assertEquals(1, actions.pendingAttitudeReviews());
        assertEquals(1, actions.pendingIndividualPlanApprovals()); assertEquals(2, actions.overdueReviews());
        assertEquals(3, actions.links().size()); assertEquals("1", actions.links().get(0).parameters().get("reviewPeriodId"));
        assertEquals("assessments", actions.links().get(0).parameters().get("tab"));
        assessments.forEach(a -> a.setSubmittedToSuperiorId(UUID.randomUUID())); attitudes.get(0).setSubmittedToSuperiorId(UUID.randomUUID());
        assertEquals(0, dashboard().attention().yourActions().pendingKpiReviews()); assertEquals(0, dashboard().attention().yourActions().pendingAttitudeReviews());
    }

    @Test void departmentCountsDoNotBecomePersonalTasksForAnotherManagersReports() {
        pendingReviews(); bob.setManager(person("Other Manager"));
        login(DEPARTMENT, "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ATTITUDE_EVALUATION");
        var result = service.dashboard(Scope.DEPARTMENT, 10L, 1L, 5, Metric.FINAL, actor.getId());
        assertEquals(2, result.attention().scopeProgress().kpiSuperiorReviews()); assertEquals(0, result.attention().yourActions().pendingKpiReviews());
        assertEquals(0, result.attention().yourActions().overdueReviews());
    }

    @Test void closedPeriodsRemainReadableButActionsAreEmpty() {
        pendingReviews(); login(TEAM, "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ATTITUDE_EVALUATION");
        current.getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        var result = dashboard(); assertTrue(result.attention().yourActions().links().isEmpty());
        assertEquals(2, result.attention().scopeProgress().kpiSuperiorReviews());
        assessments.forEach(a -> a.setStatus(KpiAssessmentStatus.REVIEWED)); attitudes.get(0).setStatus(AttitudeAssessmentStatus.REVIEWED);
        assertEquals(n("75.0000"), dashboard().summary().finalScore().score());
    }

    @Test void overdueBoundaryUsesBusinessDateAndIsASubsetOfMyPendingReviews() {
        pendingReviews(); login(TEAM, "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ATTITUDE_EVALUATION");
        checkpoints.forEach(c -> c.setSuperiorAssessmentDeadline(LocalDate.now(clock)));
        current.getReviewPeriod().setSuperiorAttitudeEvaluationDeadline(LocalDate.now(clock));
        assertEquals(0, dashboard().attention().yourActions().overdueReviews());
        checkpoints.get(0).setSuperiorAssessmentDeadline(LocalDate.now(clock).minusDays(1));
        assertEquals(1, dashboard().attention().yourActions().overdueReviews());
    }

    @Test void returnedOriginalPlansAreEmployeeWorkWhileRoutedSuperiorRevisionsCanBeMine() {
        login(TEAM, "CAN_REVIEW_INDIVIDUAL_KPI");
        plans.add(plan(current, KpiPlanStatus.RETURNED, actor.getId()));
        assertEquals(1, dashboard().attention().scopeProgress().returnedIndividualPlans()); assertEquals(0, dashboard().attention().yourActions().returnedIndividualRevisions());
        current.getReviewPeriod().setKpiAmendmentsEnabled(true);
        var revision = revision(current, actor.getId(), actor.getId(), KpiRevisionPreparationMode.SUPERIOR); revisions.add(revision);
        assertEquals(1, dashboard().attention().yourActions().returnedIndividualRevisions());
        when(roleAuthorities.existsByRoleIdAndAuthorityName(2L, AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI)).thenReturn(false);
        assertEquals(0, dashboard().attention().yourActions().returnedIndividualRevisions());
        when(roleAuthorities.existsByRoleIdAndAuthorityName(2L, AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI)).thenReturn(true);
        current.getReviewPeriod().setKpiAmendmentsCloseDate(LocalDate.now(clock).minusDays(1));
        assertEquals(0, dashboard().attention().yourActions().returnedIndividualRevisions());
    }

    @Test void selfReviewAndChangedOrInactiveReportingNeverBecomePersonalActions() {
        pendingReviews(); login(DEPARTMENT, "CAN_REVIEW_KPI_ASSESSMENT", "CAN_REVIEW_ATTITUDE_EVALUATION");
        bob.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertEquals(0, service.dashboard(Scope.DEPARTMENT, 10L, 1L, 5, Metric.FINAL, actor.getId()).attention().yourActions().pendingKpiReviews());
        bob.setAccountStatus(StaffAccountStatus.ACTIVE); current.setStaff(actor); actor.setManager(actor);
        assertEquals(0, service.dashboard(Scope.DEPARTMENT, 10L, 1L, 5, Metric.FINAL, actor.getId()).attention().yourActions().pendingKpiReviews());
    }

    @Test void pendingSelfCountsOnlyIncludeStartedWindowsNotFutureCheckpoints() {
        assessments.clear(); attitudes.clear();
        var progress = dashboard().attention().scopeProgress();
        assertEquals(1, progress.kpiSelfAssessments()); assertEquals(1, progress.attitudeSelfAssessments());
        current.getReviewPeriod().setAttitudeSelfAssessmentStartDate(LocalDate.now(clock).plusDays(1));
        assertEquals(0, dashboard().attention().scopeProgress().attitudeSelfAssessments());
    }

    @Test void batchQueriesDoNotGrowPerEmployeeOrReadAssessmentDetails() {
        for (int i = 0; i < 50; i++) enrol(personReportingToActor("Employee " + i), current.getReviewPeriod(), "50", "80", "70");
        dashboard();
        verify(kpiRepository, times(1)).findPerformanceAssessments(anyCollection()); verify(attitudeRepository, times(1)).findPerformanceAssessments(anyCollection());
        verify(checkpointRepository, times(1)).findPerformanceCheckpoints(anyCollection()); verify(repository, times(1)).findPublishedSources(anyCollection());
        assertTrue(assessments.stream().allMatch(a -> a.getItems().isEmpty()));
    }

    private com.tbm.careerpathlearning.dto.TeamPerformanceDto dashboard() { return service.dashboard(Scope.TEAM, null, 1L, 5, Metric.FINAL, actor.getId()); }
    private BigDecimal n(String value) { return new BigDecimal(value); }
    private Staff person(String name) {
        var p = new Staff(); p.setId(UUID.randomUUID()); p.setName(name); p.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(p.getId())).thenReturn(Optional.of(p)); return p;
    }
    private Staff personReportingToActor(String name) { var s = person(name); s.setManager(actor); return s; }
    private AnnualKpiReviewPeriod period(long id, int year) {
        var p = new AnnualKpiReviewPeriod(); p.setId(id); p.setName(year + " Annual Review " + id);
        p.setStartDate(LocalDate.of(year, 1, 1)); p.setEndDate(LocalDate.of(year, 12, 31)); p.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        p.setParticipantsSnapshottedAt(OffsetDateTime.now(clock)); p.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.AVERAGE);
        p.setAttitudeSelfAssessmentStartDate(LocalDate.of(year, 6, 1)); p.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(year, 7, 1));
        p.setKpiSetupDeadline(LocalDate.of(year, 12, 1)); periods.add(p); return p;
    }
    private ReviewPeriodParticipant enrol(Staff employee, AnnualKpiReviewPeriod period, String first, String last, String attitude) {
        var p = new ReviewPeriodParticipant(); p.setId(nextId++); p.setReviewPeriod(period); p.setStaff(employee); p.setStaffName(employee.getName());
        p.setDepartment(department); p.setDepartmentName("IT"); p.setRoleName("Engineer"); p.setReviewFrequency(ReviewFrequency.QUARTERLY);
        var weights = new ReviewPeriodEmployeeLevelConfiguration(); weights.setCompanyKpiWeight(n("0")); weights.setDepartmentKpiWeight(n("0")); weights.setIndividualKpiWeight(n("100"));
        p.setEmployeeLevelConfiguration(weights); population.add(p);
        var required = checkpoints.stream().filter(c -> c.getReviewPeriod() == period && c.getReviewFrequency() == p.getReviewFrequency()).toList();
        if (required.isEmpty()) {
            var c1 = checkpoint(period, 1, period.getStartDate(), LocalDate.of(period.getEndDate().getYear(), 6, 30), ReviewFrequency.QUARTERLY);
            var c2 = checkpoint(period, 2, c1.getEndDate().plusDays(1), period.getEndDate(), ReviewFrequency.QUARTERLY); required = List.of(c1, c2);
        }
        int i = 0;
        for (var c : required) {
            var a = new KpiAssessment(); a.setId(nextId++); a.setParticipant(p); a.setCheckpoint(c); a.setReviewPeriodId(period.getId());
            a.setReviewFrequency(p.getReviewFrequency()); a.setStatus(KpiAssessmentStatus.REVIEWED); a.setCheckpointScore(n(i++ == 0 ? first : last)); assessments.add(a);
        }
        var a = new AttitudeAssessment(); a.setId(nextId++); a.setParticipant(p); a.setReviewPeriodId(period.getId());
        a.setStatus(AttitudeAssessmentStatus.REVIEWED); a.setAttitudeScore(n(attitude)); attitudes.add(a); return p;
    }
    private ReviewCheckpoint checkpoint(AnnualKpiReviewPeriod period, int sequence, LocalDate start, LocalDate end, ReviewFrequency frequency) {
        var c = new ReviewCheckpoint(); c.setId(nextId++); c.setReviewPeriod(period); c.setReviewFrequency(frequency); c.setSequenceNumber(sequence);
        c.setStartDate(start); c.setEndDate(end); c.setSuperiorAssessmentDeadline(end.plusDays(14)); c.setSelfAssessmentDeadline(end.plusDays(7)); checkpoints.add(c); return c;
    }
    private ReviewCheckpoint monthly(AnnualKpiReviewPeriod p, int month) {
        var date = LocalDate.of(p.getEndDate().getYear(), month, 1);
        return checkpoint(p, month, date, date.withDayOfMonth(date.lengthOfMonth()), ReviewFrequency.MONTHLY);
    }
    private void central(ReviewPeriodParticipant p, String company, String department, String individual) {
        p.getReviewPeriod().setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS); p.getReviewPeriod().setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);
        var w = p.getEmployeeLevelConfiguration(); w.setCompanyKpiWeight(n(company)); w.setDepartmentKpiWeight(n(department)); w.setIndividualKpiWeight(n(individual));
        assessments.stream().filter(a -> a.getParticipant() == p).forEach(a -> {
            a.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS); bind(a, KpiLevel.COMPANY, 11L); bind(a, KpiLevel.DEPARTMENT, 12L);
        });
    }
    private void bind(KpiAssessment a, KpiLevel level, Long id) {
        var version = new KpiPlanVersion(); version.setId(id);
        var binding = new KpiAssessmentPlanVersion(); binding.setLevel(level); binding.setVersion(version); a.getPlanVersions().add(binding);
    }
    private void publish(Long versionId, Long checkpointId) {
        var source = mock(TeamPerformanceRepository.PublishedSource.class); when(source.getVersionId()).thenReturn(versionId);
        when(source.getCheckpointId()).thenReturn(checkpointId); published.add(source);
    }
    private TeamPerformanceRepository.VersionSource version(ReviewPeriodParticipant p, KpiLevel level, Long versionId) {
        var v = mock(TeamPerformanceRepository.VersionSource.class); when(v.getParticipantId()).thenReturn(p.getId());
        when(v.getPeriodId()).thenReturn(p.getReviewPeriod().getId()); when(v.getLevel()).thenReturn(level); when(v.getVersionId()).thenReturn(versionId); return v;
    }
    private TeamPerformanceRepository.Department department(Long id, String name) {
        var d = mock(TeamPerformanceRepository.Department.class); when(d.getId()).thenReturn(id); when(d.getName()).thenReturn(name); return d;
    }
    private TeamPerformanceRepository.IndividualPlan plan(ReviewPeriodParticipant p, KpiPlanStatus status, UUID routed) {
        var plan = mock(TeamPerformanceRepository.IndividualPlan.class); when(plan.getParticipantId()).thenReturn(p.getId());
        when(plan.getStatus()).thenReturn(status); when(plan.getSubmittedToSuperiorId()).thenReturn(routed); return plan;
    }
    private TeamPerformanceRepository.ReturnedRevision revision(ReviewPeriodParticipant p, UUID routed, UUID prepared, KpiRevisionPreparationMode mode) {
        var r = mock(TeamPerformanceRepository.ReturnedRevision.class); when(r.getParticipantId()).thenReturn(p.getId());
        when(r.getSuperiorId()).thenReturn(routed); when(r.getPreparedBy()).thenReturn(prepared); when(r.getMode()).thenReturn(mode); return r;
    }
    private void pendingReviews() {
        assessments.forEach(a -> { a.setStatus(KpiAssessmentStatus.PENDING_REVIEW); a.setSubmittedToSuperiorId(actor.getId()); });
        attitudes.get(0).setStatus(AttitudeAssessmentStatus.PENDING_REVIEW); attitudes.get(0).setSubmittedToSuperiorId(actor.getId());
    }
    private boolean inScope(ReviewPeriodParticipant p, boolean team, UUID actorId, Long departmentId) {
        return team ? !p.getStaff().isDeleted() && p.getStaff().getManager() != null && actorId.equals(p.getStaff().getManager().getId())
                : Objects.equals(departmentId, p.getDepartment() == null ? null : p.getDepartment().getId());
    }
    private void login(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getId(), null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
}
