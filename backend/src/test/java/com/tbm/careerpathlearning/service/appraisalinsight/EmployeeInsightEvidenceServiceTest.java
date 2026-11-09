package com.tbm.careerpathlearning.service.appraisalinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmployeeInsightEvidenceServiceTest {
    final EmployeeInsightSettings settings=mock(EmployeeInsightSettings.class);
    final MockEnvironment env=new MockEnvironment();final JdbcTemplate db=mock(JdbcTemplate.class);
    final EmployeeInsightScopeRepository scopes=mock(EmployeeInsightScopeRepository.class);
    final ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    final AnnualAppraisalReadinessService readiness=mock(AnnualAppraisalReadinessService.class);
    final AnnualPerformanceHistoryService history=mock(AnnualPerformanceHistoryService.class);
    final AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    final KpiAssessmentRepository assessments=mock(KpiAssessmentRepository.class);
    final KpiPlanRepository plans=mock(KpiPlanRepository.class);final KpiPlanVersionRepository versions=mock(KpiPlanVersionRepository.class);
    final KpiCentralResultRepository results=mock(KpiCentralResultRepository.class);final KpiRepository kpis=mock(KpiRepository.class);
    final AttitudeAssessmentRepository attitudes=mock(AttitudeAssessmentRepository.class);
    final CompanyKpiCheckpointResolver resolver=mock(CompanyKpiCheckpointResolver.class);
    final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule());final AppraisalInsightTemplate template=new AppraisalInsightTemplate(json);
    final EmployeeInsightEvidenceService service=new EmployeeInsightEvidenceService(settings,env,db,scopes,participants,readiness,history,appraisals,assessments,plans,versions,results,kpis,attitudes,resolver,template,json);
    final ReviewPeriodParticipant p=new ReviewPeriodParticipant();final AnnualKpiReviewPeriod period=new AnnualKpiReviewPeriod();
    final EmployeeInsightScope scope=new EmployeeInsightScope();final Map<KpiLevel,KpiPlanVersion> roots=new EnumMap<>(KpiLevel.class);
    final ReviewCheckpoint cp=new ReviewCheckpoint();final KpiAssessment assessment=new KpiAssessment();final AttitudeAssessment attitude=new AttitudeAssessment();
    AnnualAppraisalSnapshotDto snapshot;
    @BeforeEach void setup() {
        env.setActiveProfiles("uat");when(settings.isEnabled()).thenReturn(true);when(settings.getDatabaseName()).thenReturn("disposable");when(settings.getDatabaseMarker()).thenReturn("verified");
        when(db.queryForObject("select current_database()",String.class)).thenReturn("disposable");
        when(db.queryForObject("select shobj_description(oid,'pg_database') from pg_database where datname=current_database()",String.class)).thenReturn("verified");
        var staff=new Staff();staff.setId(new UUID(0,1));staff.setName("PRIVATE_EMPLOYEE_NAME");p.setStaff(staff);p.setId(7L);
        period.setId(1L);period.setName("PRIVATE_PERIOD_NAME");period.setStartDate(LocalDate.parse("2022-01-01"));period.setEndDate(LocalDate.parse("2022-12-31"));
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);p.setReviewPeriod(period);p.setReviewFrequency(ReviewFrequency.ANNUALLY);
        var department=new OrgChart();department.setId(9L);p.setDepartment(department);
        var config=new AttitudeConfiguration();config.setId(5L);period.setAttitudeConfiguration(config);
        var criterion=new AttitudeCriterion();criterion.setId(6L);criterion.setConfiguration(config);criterion.setName("Integrity");config.setCriteria(List.of(criterion));
        attitude.setId(4L);attitude.setParticipant(p);attitude.setReviewPeriodId(1L);attitude.setConfiguration(config);
        when(attitudes.findById(4L)).thenReturn(Optional.of(attitude));
        scope.setParticipantId(7L);scope.setEmployeeId(staff.getId());scope.setReviewPeriodId(1L);scope.setEnabled(true);scope.setExpectedDatabaseMarker("verified");scope.setProvenance("verified fictional fixture");
        scope.setSourceRoots(new EmployeeInsightScope.TrustRoots(Map.of("COMPANY",10L,"DEPARTMENT",20L,"INDIVIDUAL",30L),5L,List.of()));
        when(scopes.findById(7L)).thenReturn(Optional.of(scope));
        cp.setId(2L);cp.setReviewPeriod(period);cp.setStartDate(period.getStartDate());cp.setEndDate(period.getEndDate());cp.setReviewFrequency(ReviewFrequency.ANNUALLY);
        assessment.setId(3L);assessment.setParticipant(p);assessment.setReviewPeriodId(1L);assessment.setCheckpoint(cp);assessment.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
        when(assessments.findById(3L)).thenReturn(Optional.of(assessment));when(resolver.resolve(assessment)).thenReturn(cp);
        var levels=new ArrayList<KpiScoreBreakdownDto.Level>();var bindings=new ArrayList<AnnualAppraisalSnapshotDto.Version>();
        for (var level:KpiLevel.values()) {
            long id=scope.getSourceRoots().plans().get(level.name());var plan=new KpiPlan();plan.setId(id);plan.setReviewPeriod(period);plan.setLevel(level);
            plan.setDepartment(department);plan.setOwnerParticipantId(7L);when(plans.findById(id)).thenReturn(Optional.of(plan));
            var version=new KpiPlanVersion();version.setId(id+1);version.setPlan(plan);version.setReviewPeriodId(1L);version.setLevel(level);version.setVersionNumber(1);version.setStatus(KpiVersionStatus.EFFECTIVE);
            when(versions.findById(id+1)).thenReturn(Optional.of(version));roots.put(level,version);
            var k=new Kpi();k.setId(id+2);k.setVersion(version);k.setPlanId(id);k.setReviewPeriodId(1L);when(kpis.findById(id+2)).thenReturn(Optional.of(k));
            var item=new KpiItemDto();item.setId(id+2);item.setName("Delivery");item.setTarget("Complete agreed delivery");item.setWeightage(new BigDecimal("100"));
            var time=OffsetDateTime.parse("2023-01-01T00:00:00Z");var result=new KpiCentralResult();result.setId(id+3);result.setPlanVersion(version);result.setReviewPeriodId(1L);result.setLevel(level);
            result.setCheckpoint(cp);result.setReviewFrequency(cp.getReviewFrequency());result.setEditionNumber(1);result.setStatus(KpiCentralResultStatus.PUBLISHED);result.setPublishedAt(time);
            when(results.findAllByPlanVersionIdAndCheckpointIdOrderByEditionNumberDesc(id+1,2L)).thenReturn(List.of(result));
            levels.add(new KpiScoreBreakdownDto.Level(level,new BigDecimal("30"),1,1,time,KpiScoreBreakdownDto.Availability.AVAILABLE,BigDecimal.ZERO,
                    List.of(new KpiScoreBreakdownDto.Item(item,level==KpiLevel.INDIVIDUAL?null:"Recorded shared delivery",4))));
            bindings.add(new AnnualAppraisalSnapshotDto.Version(level,id+1,1));
        }
        snapshot=new AnnualAppraisalSnapshotDto(7L,1L,staff.getId(),p.getReviewFrequency(),AnnualKpiConsolidationMethod.AVERAGE,
                new BigDecimal("50"),new BigDecimal("50"),BigDecimal.ZERO,new BigDecimal("80"),new BigDecimal("40"),
                List.of(new AnnualAppraisalSnapshotDto.Checkpoint(2L,1,cp.getStartDate(),cp.getEndDate(),3L,KpiAssessmentStatus.REVIEWED,BigDecimal.ZERO,null,bindings,new KpiScoreBreakdownDto(3L,BigDecimal.ZERO,levels))),
                new AnnualAppraisalSnapshotDto.Attitude(4L,5L,AttitudeEvaluationFormat.OTHERS,AttitudeAssessmentStatus.REVIEWED,null,
                        List.of(new AnnualAppraisalSnapshotDto.Criterion(6L,"Integrity","Recorded criterion",4))));
        when(readiness.evaluate(p)).thenReturn(new AnnualAppraisalReadinessDto(true,List.of(),snapshot));
        when(history.readSelected(eq(p),any())).thenReturn(List.of());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void providerEvidencePreservesZeroAndExcludesIdentitiesAndUsesOnlyTrustedHistory(AnnualKpiReviewPeriodStatus status) throws Exception {
        period.setStatus(status);
        var captured=service.capture(p);assertEquals(BigDecimal.ZERO,captured.evidence().scores().kpi());
        var provider=json.writeValueAsString(captured.evidence());
        assertFalse(provider.contains("PRIVATE_EMPLOYEE"));assertFalse(provider.contains("PRIVATE_PERIOD"));assertFalse(provider.contains(p.getStaff().getId().toString()));
        assertTrue(provider.contains("no separate actual-achievement field"));assertTrue(provider.contains("Recorded shared delivery"));
        assertEquals(captured.fingerprint(),service.capture(p).fingerprint());assertEquals(64,captured.fingerprint().length());
        verify(history,times(2)).readSelected(p,Set.of());verify(history,never()).read(any());
    }
    @Test void closingAloneDoesNotChangeEvidenceReferencesOrFingerprint() {
        var open=service.capture(p);
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);period.setClosedAt(OffsetDateTime.parse("2023-01-15T00:00:00Z"));
        var closed=service.capture(p);
        assertEquals(open.evidence(),closed.evidence());assertEquals(open.sourceReferences(),closed.sourceReferences());
        assertEquals(open.fingerprint(),closed.fingerprint());assertSame(snapshot,readiness.evaluate(p).performance());
        verifyNoInteractions(appraisals);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void unregisteredWrongDatabaseWrongProfileAndDisabledScopesFailClosed(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        when(scopes.findById(7L)).thenReturn(Optional.empty());assertThrows(AccessDeniedException.class,()->service.capture(p));
        when(scopes.findById(7L)).thenReturn(Optional.of(scope));scope.setEnabled(false);assertThrows(AccessDeniedException.class,()->service.capture(p));scope.setEnabled(true);
        when(settings.getDatabaseName()).thenReturn("another-db");assertThrows(AccessDeniedException.class,()->service.capture(p));when(settings.getDatabaseName()).thenReturn("disposable");
        env.setActiveProfiles("dev");assertThrows(AccessDeniedException.class,()->service.capture(p));
        env.setActiveProfiles("uat","dev");assertThrows(AccessDeniedException.class,()->service.capture(p));
    }
    @Test void crossEmployeePlanAndCrossPeriodVersionsAreRejected() {
        roots.get(KpiLevel.INDIVIDUAL).getPlan().setOwnerParticipantId(99L);assertThrows(AccessDeniedException.class,()->service.capture(p));
        roots.get(KpiLevel.INDIVIDUAL).getPlan().setOwnerParticipantId(7L);roots.get(KpiLevel.COMPANY).setReviewPeriodId(99L);
        assertThrows(AccessDeniedException.class,()->service.capture(p));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void incompleteResultsAreUnavailableRatherThanReinterpretedAsZero(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        when(readiness.evaluate(p)).thenReturn(new AnnualAppraisalReadinessDto(false,List.of("Pending"),snapshot));
        assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.capture(p));verifyNoInteractions(history);
    }
    @Test void correctionsMustRemainWithinExactVersionAndCheckpointLineage() {
        var version=roots.get(KpiLevel.COMPANY);var current=results.findAllByPlanVersionIdAndCheckpointIdOrderByEditionNumberDesc(version.getId(),2L).get(0);
        var prior=new KpiCentralResult();prior.setId(50L);prior.setReviewPeriodId(99L);prior.setPlanVersion(version);prior.setCheckpoint(cp);
        prior.setEditionNumber(0);current.setPreviousEditionId(50L);when(results.findById(50L)).thenReturn(Optional.of(prior));
        assertThrows(AccessDeniedException.class,()->service.capture(p));
        current.setPreviousEditionId(null);current.setCheckpoint(new ReviewCheckpoint());assertThrows(AccessDeniedException.class,()->service.capture(p));
    }
    @Test void unverifiedHistoricalParticipantIsRejectedBeforeHistoricalResultsRead() {
        scope.setSourceRoots(new EmployeeInsightScope.TrustRoots(scope.getSourceRoots().plans(),5L,List.of(88L)));
        var other=new ReviewPeriodParticipant();other.setId(88L);var staff=new Staff();staff.setId(UUID.randomUUID());other.setStaff(staff);
        when(participants.findById(88L)).thenReturn(Optional.of(other));
        assertThrows(AccessDeniedException.class,()->service.capture(p));verifyNoInteractions(history);
    }
    @Test void nonComparableHistoryIsNotTreatedAsAnOrdinaryTrend() {
        var h=new ReviewPeriodParticipant();var previousPeriod=new AnnualKpiReviewPeriod();
        previousPeriod.setStartDate(LocalDate.parse("2021-01-01"));previousPeriod.setEndDate(LocalDate.parse("2021-12-31"));h.setReviewPeriod(previousPeriod);
        assertEquals(Boolean.TRUE,org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"comparable",p,snapshot,h,snapshot));
        previousPeriod.setStartDate(LocalDate.parse("2021-07-01"));
        assertEquals(Boolean.FALSE,org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"comparable",p,snapshot,h,snapshot));
        previousPeriod.setStartDate(LocalDate.parse("2021-01-01"));
        var different=new AnnualAppraisalSnapshotDto(snapshot.participantId(),snapshot.reviewPeriodId(),snapshot.employeeId(),
                ReviewFrequency.QUARTERLY,snapshot.consolidationMethod(),snapshot.kpiWeight(),snapshot.attitudeWeight(),snapshot.annualKpiScore(),
                snapshot.attitudeScore(),snapshot.finalScore(),snapshot.checkpoints(),snapshot.attitude());
        assertEquals(Boolean.FALSE,org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"comparable",p,snapshot,h,different));
    }
}
