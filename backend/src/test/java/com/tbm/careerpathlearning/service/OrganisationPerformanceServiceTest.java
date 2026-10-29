package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto;
import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto.*;
import com.tbm.careerpathlearning.dto.StaffPerformanceDto.Scores;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.TeamPerformanceMapperImpl;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.OrganisationPerformanceRepository;
import com.tbm.careerpathlearning.repository.AnnualPerformancePeriodRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganisationPerformanceServiceTest {
    final OrganisationPerformanceRepository repository=mock(OrganisationPerformanceRepository.class);
    final AnnualPerformancePeriodRepository periodRepository=mock(AnnualPerformancePeriodRepository.class);
    final TeamPerformanceResults results=mock(TeamPerformanceResults.class);
    final StaffPerformanceAccess access=mock(StaffPerformanceAccess.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"),ZoneOffset.UTC);
    final UUID actor=UUID.randomUUID();
    OrganisationPerformanceService service;
    final List<AnnualKpiReviewPeriod> periods=new ArrayList<>();
    final List<ReviewPeriodParticipant> population=new ArrayList<>();
    final Map<Long,Scores> scores=new HashMap<>();
    final Map<TeamPerformanceResults.Schedule,List<ReviewCheckpoint>> schedules=new HashMap<>();
    final Map<TeamPerformanceResults.AssessmentKey,KpiAssessment> assessments=new HashMap<>();
    final Map<Long,AttitudeAssessment> attitudes=new HashMap<>();
    final List<OrganisationPerformanceRepository.Decision> decisions=new ArrayList<>();
    final List<OrganisationPerformanceRepository.AppraisalProgress> appraisalProgress=new ArrayList<>();
    AnnualKpiReviewPeriod current;OrgChart it,finance;long next=100;
    @BeforeEach void setup() {
        when(access.actor(actor)).thenReturn(new Staff());when(access.has(StaffPerformanceAccess.ORGANISATION)).thenReturn(true);
        when(periodRepository.findPeriods()).thenReturn(periods);
        when(repository.findPopulation(anyCollection())).thenAnswer(i -> population.stream().filter(p -> ((Collection<?>)i.getArgument(0)).contains(p.getReviewPeriod().getId())).toList());
        when(repository.findDecisions(anyLong())).thenReturn(decisions);when(repository.findAppraisalProgress(anyLong())).thenReturn(appraisalProgress);
        when(results.load(anyList())).thenReturn(new TeamPerformanceResults.Data(scores,schedules,assessments,attitudes,List.of(),List.of()));
        service=new OrganisationPerformanceService(repository,periodRepository,results,access,new TeamPerformanceMapperImpl(),clock);
        it=department(10,"IT recorded name");finance=department(20,"Finance");current=period(1,2025);
    }
    @AfterEach void readOnly() {
        for(var call:mockingDetails(repository).getInvocations())assertTrue(call.getMethod().getName().startsWith("find"),call.toString());
    }
    @Test void averagesAreEmployeeWeightedWithIndependentContributorsNotDepartmentAverages() {
        enrol(current,it,"0","80");enrol(current,it,"100","100");enrol(current,finance,"80",null);
        var report=dashboard();assertEquals(3,report.summary().totalEmployees());assertEquals(2,report.summary().employeesWithResults());
        assertEquals(n("60.0000"),report.summary().kpi().score());assertEquals(3,report.summary().kpi().contributingEmployees());
        assertEquals(n("90.0000"),report.summary().attitude().score());assertEquals(2,report.summary().attitude().contributingEmployees());
        assertEquals(n("70.0000"),report.summary().finalScore().score());
        assertEquals(2,report.departments().size());assertEquals(1,report.distribution().missingResults());
        verify(results,times(1)).load(anyList());assertTrue(OrganisationPerformanceService.class.getAnnotation(Transactional.class).readOnly());
    }
    @Test void zeroesAreCompletedAndEmptyPublishedPeriodsHaveNullAverages() {
        var p=enrol(current,it,"0","0");assertEquals(n("0.0000"),dashboard().summary().finalScore().score());
        assertEquals(1,dashboard().distribution().bins().get(0).employees());population.remove(p);
        var empty=dashboard();assertNull(empty.summary().kpi().score());assertEquals(0,empty.summary().totalEmployees());assertTrue(empty.departments().isEmpty());
    }
    @Test void retainsRecordedDepartmentAndIncludesUnassignedAndDeletedHistoricalParticipants() {
        var p=enrol(current,it,"60","80");p.getStaff().setDeleted(true);it.setName("Renamed today");enrol(current,null,"80","100");
        var report=dashboard();assertEquals(2,report.summary().totalEmployees());
        assertTrue(report.departments().stream().anyMatch(d -> d.departmentId()==null && d.name().equals("Unassigned Department")));
        assertTrue(report.departments().stream().anyMatch(d -> d.name().equals("IT recorded name")));
    }
    @Test void progressSeparatesKpiAndAttitudeTasksFromPendingHrAndCarriesNoActions() {
        var p=enrol(current,it,null,null);var a=assessments.get(new TeamPerformanceResults.AssessmentKey(p.getId(),current.getId()));a.setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        var pending=mock(OrganisationPerformanceRepository.AppraisalProgress.class);when(pending.getParticipantId()).thenReturn(p.getId());when(pending.getStatus()).thenReturn(AppraisalStatus.PENDING_REVIEW);appraisalProgress.add(pending);
        var d=dashboard().departments().get(0);assertEquals(1,d.assessmentProgress().kpiSuperiorReviews());assertEquals(0,d.assessmentProgress().kpiSelfAssessments());
        assertEquals(1,d.assessmentProgress().attitudeSelfAssessments());assertEquals(1,d.pendingHrReview());
        a.setStatus(KpiAssessmentStatus.DRAFT);current.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertEquals(1,dashboard().departments().get(0).assessmentProgress().kpiSelfAssessments());
    }
    @Test void trendPreservesPeriodIdsDatesAndSameYearPointsWithoutFutureOrOlderPeriods() {
        enrol(current,it,"60","80");var earlier=period(2,2025);earlier.setName("Separate period");earlier.setEndDate(LocalDate.of(2025,6,30));enrol(earlier,it,"70","90");
        enrol(period(3,2026),it,"90","90");enrol(period(4,2019),it,"90","90");
        var report=dashboard();assertEquals(List.of(2L,1L),report.trend().stream().map(p -> p.period().id()).toList());
        assertEquals(List.of(2L,1L),report.departmentTrends().get(0).periods().stream().map(p -> p.period().id()).toList());
        verify(repository).findPopulation(List.of(2L,1L));
    }
    @Test void departmentTrendsKeepHistoricalOnlyDepartmentsAndDoNotFabricateEmptyMembership() {
        enrol(current,it,"80","80");enrol(period(2,2024),finance,"60","60");
        var report=dashboard();assertEquals(1,report.departments().size());assertEquals(2,report.departmentTrends().size());
        assertTrue(report.departmentTrends().stream().allMatch(d -> d.periods().size()==1));
    }
    @Test void yoyUsesPercentagePointsAndRecordedScheduleCompatibility() {
        enrol(current,it,"80","80");var prior=period(2,2024);var p=enrol(prior,it,"60","60");
        assertEquals(n("20.0000"),dashboard().yearOnYear().percentagePoints());
        p.setReviewFrequency(ReviewFrequency.MONTHLY);assertEquals(Comparison.INCOMPATIBLE_CONFIGURATION,dashboard().departments().get(0).yearOnYear().status());
    }
    @Test void yoyDoesNotPickAnArbitraryPreviousPeriodOrTurnMissingScoresIntoZero() {
        enrol(current,it,"80",null);enrol(period(2,2024),it,"60","60");assertEquals(Comparison.NO_COMPARABLE_RESULTS,dashboard().yearOnYear().status());
        period(3,2024);assertEquals(Comparison.AMBIGUOUS_PREVIOUS_PERIOD,dashboard().yearOnYear().status());assertNull(dashboard().yearOnYear().previousPeriodId());
    }
    @Test void metricSelectionUsesTheExistingNeutralScoreBands() {
        enrol(current,it,"0","100");enrol(current,finance,"100",null);
        var kpi=service.dashboard(1L,3,Metric.KPI,actor);assertEquals(2,kpi.distribution().employeesWithResults());assertEquals(1,kpi.distribution().bins().get(0).employees());
        var attitude=service.dashboard(1L,5,Metric.ATTITUDE,actor);assertEquals(1,attitude.distribution().missingResults());assertEquals(1,attitude.distribution().bins().get(4).employees());
    }
    @Test void onlyOrganisationPermissionAllowsServiceReadsAndExportSourceData() {
        when(access.has(StaffPerformanceAccess.ORGANISATION)).thenReturn(false);
        assertThrows(AccessDeniedException.class,() -> service.periods(actor));assertThrows(AccessDeniedException.class,this::dashboard);verifyNoInteractions(repository,periodRepository,results);
    }
    @Test void rejectsMalformedUnavailableAndUnsupportedSelections() {
        assertThrows(BadRequestException.class,() -> service.dashboard(null,5,Metric.FINAL,actor));assertThrows(BadRequestException.class,() -> service.dashboard(-1L,5,Metric.FINAL,actor));
        assertThrows(BadRequestException.class,() -> service.dashboard(1L,4,Metric.FINAL,actor));assertThrows(BadRequestException.class,() -> service.dashboard(1L,5,null,actor));
        assertThrows(AccessDeniedException.class,() -> service.dashboard(99L,5,Metric.FINAL,actor));verify(results,never()).load(anyList());
    }
    @Test void allOrganisationViewersCanSeeSafelyAggregatedRecordedDecisionCounts() {
        for(int i=0;i<10;i++){var p=enrol(current,it,"80","80");decisions.add(decision(p,AppraisalStatus.APPROVED,i<5?AnnualAppraisalRecommendation.PROMOTION:AnnualAppraisalRecommendation.SALARY_INCREMENT,i<5?AnnualAppraisalRecommendation.SALARY_INCREMENT:AnnualAppraisalRecommendation.PROMOTION));}
        var report=dashboard();var distribution=report.appraisalDistribution();
        assertEquals(Disclosure.AVAILABLE,distribution.superiorRecommendations().disclosure());assertEquals(10,distribution.superiorRecommendations().total());
        assertEquals(5,distribution.superiorRecommendations().counts().stream().filter(d -> d.decision()==AnnualAppraisalRecommendation.PROMOTION).findFirst().orElseThrow().count());
        assertEquals(Disclosure.AVAILABLE,distribution.hrFinalDecisions().disclosure());verify(access,never()).has("CAN_REVIEW_ANNUAL_APPRAISAL");
    }
    @Test void smallOrSparseSeriesWithholdAllCountsAndTotalsToPreventSubtraction() {
        for(int i=0;i<6;i++){var p=enrol(current,it,"80","80");decisions.add(decision(p,AppraisalStatus.APPROVED,i==0?AnnualAppraisalRecommendation.PROMOTION:AnnualAppraisalRecommendation.SALARY_INCREMENT,AnnualAppraisalRecommendation.BOTH));}
        var data=dashboard().appraisalDistribution();assertEquals(Disclosure.SUPPRESSED,data.superiorRecommendations().disclosure());
        assertNull(data.superiorRecommendations().total());assertTrue(data.superiorRecommendations().counts().isEmpty());
        assertEquals(Disclosure.SUPPRESSED,data.hrFinalDecisions().disclosure());assertNull(data.hrFinalDecisions().total());
    }
    @Test void homogeneousOutcomeSeriesDoNotIdentifyAllMembersByPublishingASingleCategory() {
        for(int i=0;i<12;i++){var p=enrol(current,it,"80","80");decisions.add(decision(p,AppraisalStatus.APPROVED,AnnualAppraisalRecommendation.PROMOTION,AnnualAppraisalRecommendation.PROMOTION));}
        assertEquals(Disclosure.SUPPRESSED,dashboard().appraisalDistribution().hrFinalDecisions().disclosure());
    }
    @Test void draftsAndMissingSubmissionActivityCannotBecomeSubmittedRecommendations() {
        var p=enrol(current,it,"80","80");decisions.add(decision(p,AppraisalStatus.DRAFT,AnnualAppraisalRecommendation.PROMOTION,null));
        decisions.add(decision(p,AppraisalStatus.RETURNED,null,null));var report=dashboard();
        assertEquals(Disclosure.NO_DATA,report.appraisalDistribution().superiorRecommendations().disclosure());assertEquals(0,report.appraisalDistribution().superiorRecommendations().total());
    }
    @Test void approvedResultsRemainSeparateFromRecordedDecisionsAfterCorrections() {
        var p=enrol(current,it,"40","60");var recorded=decision(p,AppraisalStatus.APPROVED,AnnualAppraisalRecommendation.NO_RECOMMENDATION,AnnualAppraisalRecommendation.PROMOTION);decisions.add(recorded);
        var first=dashboard();scores.put(p.getId(),new Scores(n("100"),n("100"),n("100"),n("50"),n("50"),AnnualKpiConsolidationMethod.AVERAGE));
        var after=dashboard();assertEquals(n("100.0000"),after.summary().finalScore().score());assertEquals(first.appraisalDistribution(),after.appraisalDistribution());
    }
    OrganisationPerformanceDto dashboard(){return service.dashboard(1L,5,Metric.FINAL,actor);}
    AnnualKpiReviewPeriod period(long id,int year) {
        var p=new AnnualKpiReviewPeriod();p.setId(id);p.setName("Annual "+year);p.setStartDate(LocalDate.of(year,1,1));p.setEndDate(LocalDate.of(year,12,31));p.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        p.setParticipantsSnapshottedAt(OffsetDateTime.now(clock));p.setFrequencyPolicy(KpiFrequencyPolicy.ROLE_BASED);p.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);p.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.AVERAGE);
        p.setKpiPerformanceWeight(n("50"));p.setAttitudeEvaluationWeight(n("50"));p.setAttitudeSelfAssessmentStartDate(p.getEndDate());periods.add(p);return p;
    }
    ReviewPeriodParticipant enrol(AnnualKpiReviewPeriod period,OrgChart department,String kpi,String attitude) {
        var p=new ReviewPeriodParticipant();p.setId(next++);p.setReviewPeriod(period);p.setDepartment(department);p.setDepartmentName(department==null?null:department.getName());p.setReviewFrequency(ReviewFrequency.ANNUALLY);
        var staff=new Staff();staff.setId(UUID.randomUUID());p.setStaff(staff);p.setStaffName("Employee "+p.getId());
        var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setCompanyKpiWeight(n("15"));weights.setDepartmentKpiWeight(n("15"));weights.setIndividualKpiWeight(n("70"));p.setEmployeeLevelConfiguration(weights);
        population.add(p);scores.put(p.getId(),new Scores(n(kpi),n(attitude),kpi==null||attitude==null?null:n(kpi).add(n(attitude)).divide(n("2")),n("50"),n("50"),period.getAnnualKpiConsolidationMethod()));
        var c=new ReviewCheckpoint();c.setId(period.getId());c.setReviewPeriod(period);c.setStartDate(period.getStartDate());c.setEndDate(period.getEndDate());c.setReviewFrequency(ReviewFrequency.ANNUALLY);c.setSequenceNumber(1);
        schedules.put(new TeamPerformanceResults.Schedule(period.getId(),p.getReviewFrequency()),List.of(c));
        var a=new KpiAssessment();a.setParticipant(p);a.setCheckpoint(c);a.setStatus(kpi==null?KpiAssessmentStatus.DRAFT:KpiAssessmentStatus.REVIEWED);assessments.put(new TeamPerformanceResults.AssessmentKey(p.getId(),c.getId()),a);
        if(attitude!=null){var evaluation=new AttitudeAssessment();evaluation.setStatus(AttitudeAssessmentStatus.REVIEWED);attitudes.put(p.getId(),evaluation);}
        return p;
    }
    OrgChart department(long id,String name){var d=new OrgChart();d.setId(id);d.setName(name);return d;}
    OrganisationPerformanceRepository.Decision decision(ReviewPeriodParticipant p,AppraisalStatus status,AnnualAppraisalRecommendation recommendation,AnnualAppraisalRecommendation finalDecision) {
        var d=mock(OrganisationPerformanceRepository.Decision.class);when(d.getParticipantId()).thenReturn(p.getId());when(d.getStatus()).thenReturn(status);when(d.getSubmittedAt()).thenReturn(OffsetDateTime.now(clock));
        when(d.getReviewedAt()).thenReturn(OffsetDateTime.now(clock));when(d.getSubmittedRecommendation()).thenReturn(recommendation);when(d.getFinalDecision()).thenReturn(finalDecision);return d;
    }
    static BigDecimal n(String value){return value==null?null:new BigDecimal(value);}
}
