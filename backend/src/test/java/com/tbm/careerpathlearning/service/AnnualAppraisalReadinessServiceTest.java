package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnualAppraisalReadinessServiceTest {
    ReviewCheckpointRepository checkpoints=mock(ReviewCheckpointRepository.class);
    KpiAssessmentRepository assessments=mock(KpiAssessmentRepository.class);
    AttitudeAssessmentRepository attitudes=mock(AttitudeAssessmentRepository.class);
    KpiCheckpointAggregateService aggregates=mock(KpiCheckpointAggregateService.class);
    KpiPlanMapper mapper=mock(KpiPlanMapper.class);
    AnnualAppraisalReadinessService service=new AnnualAppraisalReadinessService(checkpoints,assessments,attitudes,aggregates,mapper);
    AnnualKpiReviewPeriod period;
    ReviewPeriodParticipant participant;
    AttitudeAssessment attitude;
    List<KpiAssessment> records;

    @BeforeEach void setup() {
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.AVERAGE);
        var employee=new Staff();employee.setId(UUID.randomUUID());
        participant=new ReviewPeriodParticipant();participant.setId(2L);participant.setStaff(employee);participant.setReviewPeriod(period);
        participant.setReviewFrequency(ReviewFrequency.QUARTERLY);
        attitude=new AttitudeAssessment();attitude.setId(3L);attitude.setParticipant(participant);
        attitude.setStatus(AttitudeAssessmentStatus.REVIEWED);attitude.setAttitudeScore(new BigDecimal("80.0000"));
        var config=new AttitudeConfiguration();config.setId(4L);attitude.setConfiguration(config);
        attitude.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);
        records=new ArrayList<>();
        when(attitudes.findByParticipantId(2L)).thenAnswer(i->Optional.ofNullable(attitude));
        when(assessments.findAllByParticipantId(2L)).thenAnswer(i->records);
        when(aggregates.pendingLevels(any())).thenReturn(List.of());
        when(aggregates.breakdown(any())).thenAnswer(i->{KpiAssessment a=i.getArgument(0);return new KpiScoreBreakdownDto(a.getId(),a.getCheckpointScore(),List.of());});
        results(ReviewFrequency.QUARTERLY,"60","80","100","80");
    }
    void results(ReviewFrequency frequency,String... scores) {
        participant.setReviewFrequency(frequency);records.clear();List<ReviewCheckpoint> required=new ArrayList<>();
        for(int i=0;i<scores.length;i++) {
            var c=new ReviewCheckpoint();c.setId(10L+i);c.setSequenceNumber(i+1);c.setReviewPeriod(period);c.setReviewFrequency(frequency);
            c.setStartDate(LocalDate.of(2027,1,1).plusMonths(i));c.setEndDate(c.getStartDate().plusMonths(1).minusDays(1));required.add(c);
            var a=new KpiAssessment();a.setId(20L+i);a.setParticipant(participant);a.setCheckpoint(c);a.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
            a.setStatus(KpiAssessmentStatus.REVIEWED);a.setCheckpointScore(scores[i]==null?null:new BigDecimal(scores[i]));records.add(a);
        }
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L,frequency)).thenReturn(required);
    }
    @Test void averageAndFinalUseConfiguredWeightsAndDecimalScores() {
        var average=service.evaluate(participant);assertTrue(average.ready());
        assertEquals(new BigDecimal("80.0000"),average.performance().annualKpiScore());
        period.setKpiPerformanceWeight(new BigDecimal("70"));period.setAttitudeEvaluationWeight(new BigDecimal("30"));
        period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        records.get(3).setCheckpointScore(new BigDecimal("93.3333"));
        assertEquals(new BigDecimal("89.3333"),service.evaluate(participant).performance().finalScore());
        verify(assessments,never()).saveAndFlush(any());
    }
    @ParameterizedTest @EnumSource(ReviewFrequency.class)
    void readinessFollowsTheRecordedEmployeeFrequency(ReviewFrequency frequency) {
        int count=switch(frequency) {case MONTHLY->12;case QUARTERLY->4;case ANNUALLY->1;};
        var scores=new String[count];Arrays.fill(scores,"80");results(frequency,scores);
        var dto=service.evaluate(participant);assertTrue(dto.ready());assertEquals(count,dto.performance().checkpoints().size());
        assertEquals(frequency,dto.performance().reviewFrequency());
    }
    @Test void finalCheckpointDoesNotExcuseMissingEarlierReviews() {
        period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        records.get(0).setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        var dto=service.evaluate(participant);assertFalse(dto.ready());assertNull(dto.performance().finalScore());
        assertTrue(dto.blockers().stream().anyMatch(s->s.contains("Superior KPI review")));
    }
    @Test void missingCentralResultsBlockAppraisalButARealZeroDoesNot() {
        records.get(0).setCheckpointScore(null);when(aggregates.pendingLevels(records.get(0))).thenReturn(List.of(KpiLevel.COMPANY));
        var pending=service.evaluate(participant);assertFalse(pending.ready());assertNull(pending.performance().finalScore());
        assertTrue(pending.blockers().stream().anyMatch(s->s.contains("COMPANY")));
        results(ReviewFrequency.QUARTERLY,"0","0","0","0");attitude.setAttitudeScore(BigDecimal.ZERO);
        assertTrue(service.evaluate(participant).ready());assertEquals(new BigDecimal("0.0000"),service.evaluate(participant).performance().finalScore());
    }
    @Test void zeroWeightsDoNotInventCompletionExemptions() {
        period.setKpiPerformanceWeight(new BigDecimal("100"));period.setAttitudeEvaluationWeight(BigDecimal.ZERO);attitude=null;
        assertFalse(service.evaluate(participant).ready());
    }
    @Test void allSuperiorAttitudeWorkMustBeCompleted() {
        attitude.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);
        assertFalse(service.evaluate(participant).ready());assertNull(service.evaluate(participant).performance().finalScore());
    }
    @Test void noCheckpointsMissingMethodAndInvalidWeightsAreExplained() {
        results(ReviewFrequency.QUARTERLY);period.setAnnualKpiConsolidationMethod(null);period.setKpiPerformanceWeight(new BigDecimal("60"));
        var dto=service.evaluate(participant);assertFalse(dto.ready());assertEquals(3,dto.blockers().size());
    }
    @Test void legacyModelReadsItsOfficialScoresWithoutCentralResultDependencies() {
        records.forEach(a->a.setAssessmentModel(KpiAssessmentModel.LEGACY_ALL_LEVELS));
        var dto=service.evaluate(participant);assertTrue(dto.ready());assertEquals(new BigDecimal("80.0000"),dto.performance().finalScore());
        verifyNoInteractions(aggregates);
    }
    @Test void sourcesIncludeVersionIdsAndAttitudeDefinitionsAndPoints() {
        var version=new KpiPlanVersion();version.setId(90L);version.setVersionNumber(2);
        var binding=new KpiAssessmentPlanVersion();binding.setLevel(KpiLevel.INDIVIDUAL);binding.setVersion(version);records.get(0).getPlanVersions().add(binding);
        var criterion=new AttitudeCriterion();criterion.setId(91L);criterion.setName("Respect");criterion.setDescription("Works respectfully");
        var item=new AttitudeAssessmentItem();item.setCriterion(criterion);item.setSuperiorPoint(4);attitude.getItems().add(item);
        var snapshot=service.evaluate(participant).performance();
        assertEquals(90L,snapshot.checkpoints().get(0).versions().get(0).versionId());
        assertEquals("Respect",snapshot.attitude().criteria().get(0).name());assertEquals(4,snapshot.attitude().criteria().get(0).superiorPoint());
    }

    @ParameterizedTest @EnumSource(KpiAssessmentModel.class)
    void performanceHistoryReusesActualReadOnlyCalculationsWithoutAggregateRefresh(KpiAssessmentModel model) {
        records.forEach(a->a.setAssessmentModel(model));
        period.setStartDate(LocalDate.of(2027,1,1));period.setEndDate(LocalDate.of(2027,12,31));
        period.setName("Completed Annual Results");period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        var selected=new AnnualKpiReviewPeriod();selected.setId(10L);selected.setEndDate(LocalDate.of(2028,12,31));
        var anchor=new ReviewPeriodParticipant();anchor.setStaff(participant.getStaff());anchor.setReviewPeriod(selected);
        var participants=mock(ReviewPeriodParticipantRepository.class);var appraisals=mock(AnnualAppraisalRepository.class);
        var events=mock(AnnualAppraisalActivityRepository.class);
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(participant.getStaff().getId())).thenReturn(List.of(participant));
        var history=new AnnualPerformanceHistoryService(participants,appraisals,events,checkpoints,service,new com.fasterxml.jackson.databind.ObjectMapper());
        var result=history.read(anchor).get(0);
        assertEquals(new BigDecimal("80.0000"),result.finalScore());
        assertEquals(AnnualPerformanceHistoryDto.SourceType.CALCULATED_RESULTS,result.sourceType());
        verifyNoInteractions(events);
        for(var invocation:mockingDetails(aggregates).getInvocations())
            assertTrue(List.of("pendingLevels","breakdown").contains(invocation.getMethod().getName()));
        for(var invocation:mockingDetails(assessments).getInvocations()) assertEquals("findAllByParticipantId",invocation.getMethod().getName());
        for(var invocation:mockingDetails(attitudes).getInvocations()) assertEquals("findByParticipantId",invocation.getMethod().getName());
    }
}
