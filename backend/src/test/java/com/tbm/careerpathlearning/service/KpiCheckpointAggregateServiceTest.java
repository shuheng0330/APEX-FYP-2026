package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KpiCheckpointAggregateServiceTest {
    private final KpiCheckpointResultRepository aggregateRows=mock(KpiCheckpointResultRepository.class);
    private final KpiCentralResultRepository central=mock(KpiCentralResultRepository.class);
    private final KpiAssessmentRepository assessments=mock(KpiAssessmentRepository.class);
    private final EmployeeKpiAssignmentRepository assignments=mock(EmployeeKpiAssignmentRepository.class);
    private final ReviewCheckpointRepository checkpoints=mock(ReviewCheckpointRepository.class);
    private final UUID actor=UUID.randomUUID();
    private final Clock clock=Clock.fixed(Instant.parse("2027-02-02T12:00:00Z"),ZoneOffset.UTC);
    private final KpiCheckpointAggregateService service=new KpiCheckpointAggregateService(
            aggregateRows,central,assessments,assignments,clock,
            org.mapstruct.factory.Mappers.getMapper(com.tbm.careerpathlearning.mapper.KpiPlanMapper.class),new CompanyKpiCheckpointResolver(checkpoints));
    private final KpiCheckpointResult[] stored={null};
    private final Map<Long,KpiCentralResult> publications=new HashMap<>();

    private KpiPlanVersion version(KpiLevel level,long id) {
        var plan=new KpiPlan();plan.setLevel(level);
        plan.setStatus(level==KpiLevel.COMPANY?KpiPlanStatus.PUBLISHED:KpiPlanStatus.APPROVED);
        var version=new KpiPlanVersion();version.setId(id);version.setLevel(level);
        version.setPlan(plan);plan.setCurrentVersion(version);return version;
    }
    private KpiCentralResult publication(KpiPlanVersion version,long id,int point) {
        var result=new KpiCentralResult();result.setId(id);result.setPlanVersion(version);
        result.setLevel(version.getLevel());
        result.setStatus(KpiCentralResultStatus.PUBLISHED);result.setReviewPeriodId(1L);
        var checkpoint=new ReviewCheckpoint();checkpoint.setId(5L);result.setCheckpoint(checkpoint);
        var item=new KpiCentralResultItem();item.setResult(result);item.setPoint(point);
        var kpi=new Kpi();kpi.setWeightage(new BigDecimal("100"));item.setKpi(kpi);
        result.getItems().add(item);return result;
    }
    private KpiAssessment reviewed(BigDecimal company,BigDecimal department,BigDecimal individual) {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);period.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
        var participant=new ReviewPeriodParticipant();participant.setId(3L);participant.setReviewPeriod(period);
        var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setCompanyKpiWeight(company);
        weights.setDepartmentKpiWeight(department);weights.setIndividualKpiWeight(individual);
        participant.setEmployeeLevelConfiguration(weights);
        var checkpoint=new ReviewCheckpoint();checkpoint.setId(5L);checkpoint.setReviewPeriod(period);
        var assessment=new KpiAssessment();assessment.setId(8L);assessment.setParticipant(participant);
        assessment.setReviewPeriodId(1L);assessment.setCheckpoint(checkpoint);assessment.setReviewFrequency(ReviewFrequency.MONTHLY);
        assessment.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);assessment.setStatus(KpiAssessmentStatus.REVIEWED);
        for(var level:KpiLevel.values()) {
            var v=version(level,10L+level.ordinal());var binding=new KpiAssessmentPlanVersion();
            binding.setLevel(level);binding.setVersion(v);binding.setAssessment(assessment);assessment.getPlanVersions().add(binding);
            if(level==KpiLevel.INDIVIDUAL) {
                var kpi=new Kpi();kpi.setPlan(v.getPlan());kpi.setWeightage(new BigDecimal("100"));
                var assignment=new EmployeeKpiAssignment();assignment.setKpi(kpi);
                var item=new KpiAssessmentItem();item.setAssignment(assignment);item.setSuperiorPoint(5);
                item.setSelfPoint(1);assessment.getItems().add(item);
            }
        }
        when(assignments.findAllByParticipantId(3L)).thenReturn(List.of());
        when(aggregateRows.lockByScope(3L,5L)).thenAnswer(i->Optional.ofNullable(stored[0]));
        when(aggregateRows.saveAndFlush(any())).thenAnswer(i->{stored[0]=i.getArgument(0);return stored[0];});
        when(assessments.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(assessments.findAllByReviewPeriodIdAndCheckpointIdAndStatus(1L,5L,KpiAssessmentStatus.REVIEWED))
                .thenReturn(List.of(assessment));
        when(central.findByPlanVersionIdAndCheckpointIdAndStatus(anyLong(),eq(5L),eq(KpiCentralResultStatus.PUBLISHED)))
                .thenAnswer(i->Optional.ofNullable(publications.get(i.getArgument(0))));
        return assessment;
    }

    @Test void quarterlyAssessmentUsesOnlyMarchCompanyResultAndRefreshesOnCorrection() {
        var assessment=reviewed(new BigDecimal("50"),BigDecimal.ZERO,new BigDecimal("50"));
        var period=assessment.getParticipant().getReviewPeriod();period.setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);
        assessment.setReviewFrequency(ReviewFrequency.QUARTERLY);
        assessment.getCheckpoint().setReviewFrequency(ReviewFrequency.QUARTERLY);
        assessment.getCheckpoint().setEndDate(LocalDate.of(2027,3,31));
        var march=new ReviewCheckpoint();march.setId(30L);march.setReviewPeriod(period);march.setReviewFrequency(ReviewFrequency.MONTHLY);
        march.setStartDate(LocalDate.of(2027,3,1));march.setEndDate(LocalDate.of(2027,3,31));
        when(checkpoints.findByReviewPeriodIdAndReviewFrequencyAndEndDate(1L,ReviewFrequency.MONTHLY,march.getEndDate())).thenReturn(Optional.of(march));
        var version=assessment.getPlanVersions().get(0).getVersion();
        when(central.findByPlanVersionIdAndCheckpointIdAndStatus(version.getId(),30L,KpiCentralResultStatus.PUBLISHED))
                .thenAnswer(i->Optional.ofNullable(publications.get(version.getId())));
        service.refresh(assessment,actor,"Review");assertNull(assessment.getCheckpointScore());
        assertEquals(30L,stored[0].getCompanyCheckpointId());assertEquals(5L,stored[0].getCheckpointId());
        assertEquals(List.of(KpiLevel.COMPANY),service.pendingLevels(assessment));
        when(assessments.findAllByReviewPeriodIdAndCheckpointEndDateAndStatus(1L,march.getEndDate(),KpiAssessmentStatus.REVIEWED))
                .thenReturn(List.of(assessment));
        var result=publication(version,300L,4);result.setCheckpoint(march);publications.put(version.getId(),result);
        service.refreshForPublication(result,actor);assertEquals(new BigDecimal("90.0000"),assessment.getCheckpointScore());
        var breakdown=service.breakdown(assessment).levels().get(0);
        assertEquals(30L,breakdown.reportingCheckpoint().id());assertEquals(march.getEndDate(),breakdown.reportingCheckpoint().endDate());
        var correction=publication(version,301L,5);correction.setCheckpoint(march);publications.put(version.getId(),correction);
        service.refreshForPublication(correction,actor);assertEquals(new BigDecimal("100.0000"),assessment.getCheckpointScore());
        assertEquals(1,assessment.getItems().get(0).getSelfPoint());
        assertEquals(2,stored[0].getCalculationHistory().size());
        verify(central,never()).findByPlanVersionIdAndCheckpointIdAndStatus(version.getId(),5L,KpiCentralResultStatus.PUBLISHED);
    }

    @Test void missingMatchingMonthlyCheckpointDoesNotFallBackToQuarterlyOrEarlierResult() {
        var assessment=reviewed(new BigDecimal("50"),BigDecimal.ZERO,new BigDecimal("50"));
        assessment.getParticipant().getReviewPeriod().setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);
        assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.refresh(assessment,actor,"Review"));
        verify(aggregateRows,never()).saveAndFlush(any());
    }

    @Test void pendingIsNullThenPublishesAndCorrectsWithoutChangingIndividualAnswers() {
        var assessment=reviewed(new BigDecimal("50"),new BigDecimal("30"),new BigDecimal("20"));
        assertEquals(List.of(KpiLevel.COMPANY,KpiLevel.DEPARTMENT),service.pendingLevels(assessment));
        service.refresh(assessment,actor,"Individual review completed");
        assertNull(stored[0].getScore());assertNull(assessment.getCheckpointScore());
        assertTrue(stored[0].getCalculationHistory().isEmpty());
        var company=publication(assessment.getPlanVersions().get(0).getVersion(),100L,4);
        publications.put(company.getPlanVersion().getId(),company);
        service.refreshForPublication(company,actor);
        assertNull(assessment.getCheckpointScore());
        var department=publication(assessment.getPlanVersions().get(1).getVersion(),200L,3);
        publications.put(department.getPlanVersion().getId(),department);
        service.refreshForPublication(department,actor);
        assertEquals(0,new BigDecimal("78.0000").compareTo(assessment.getCheckpointScore()));
        assertEquals(1,stored[0].getCalculationHistory().size());
        assertEquals(List.of(),service.pendingLevels(assessment));
        assertEquals(1,assessment.getItems().get(0).getSelfPoint());
        var correction=publication(company.getPlanVersion(),101L,5);
        publications.put(correction.getPlanVersion().getId(),correction);
        service.refreshForPublication(correction,actor);
        assertEquals(0,new BigDecimal("88.0000").compareTo(assessment.getCheckpointScore()));
        assertEquals(2,stored[0].getCalculationHistory().size());
        assertEquals(100L,stored[0].getCalculationHistory().get(0).companyResultId());
        assertEquals(101L,stored[0].getCalculationHistory().get(1).companyResultId());
        assertEquals(1,assessment.getItems().get(0).getSelfPoint());
    }

    @Test void zeroAllocationsDoNotRequireMissingCentralPublications() {
        var assessment=reviewed(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("100"));
        assertTrue(service.pendingLevels(assessment).isEmpty());
        service.refresh(assessment,actor,"Individual review completed");
        assertEquals(0,new BigDecimal("100").compareTo(assessment.getCheckpointScore()));
        assertNull(stored[0].getCompanyResultId());assertNull(stored[0].getDepartmentResultId());
    }

    @Test void breakdownShowsPublishedContributionsWhileHidingUnfinishedSuperiorPoints() {
        var assessment=reviewed(new BigDecimal("50"),new BigDecimal("30"),new BigDecimal("20"));
        assessment.setStatus(KpiAssessmentStatus.PENDING_REVIEW);
        var version=assessment.getPlanVersions().get(0).getVersion();
        version.setVersionNumber(2);
        var company=publication(version,100L,4);company.setEditionNumber(3);
        company.getItems().get(0).setActualAchievement("80 units");
        company.getItems().get(0).setComment("Internal result preparation note");
        publications.put(version.getId(),company);
        var dto=service.breakdown(assessment);
        assertNull(dto.checkpointScore());
        var published=dto.levels().get(0);
        assertEquals(com.tbm.careerpathlearning.dto.KpiScoreBreakdownDto.Availability.AVAILABLE,published.status());
        assertEquals(0,new BigDecimal("40").compareTo(published.contribution()));
        assertEquals(2,published.versionNumber());assertEquals(3,published.resultEditionNumber());
        assertEquals("80 units",published.items().get(0).actualAchievement());
        assertEquals(4,published.items().get(0).point());
        assertNull(dto.levels().get(1).contribution());
        assertTrue(dto.levels().get(1).items().isEmpty());
        assertNull(dto.levels().get(2).contribution());
        assertTrue(dto.levels().get(2).items().isEmpty());
        verify(aggregateRows,never()).saveAndFlush(any());
        verify(assessments,never()).saveAndFlush(any());
        verify(central).findByPlanVersionIdAndCheckpointIdAndStatus(version.getId(),5L,KpiCentralResultStatus.PUBLISHED);
    }

    @Test void breakdownUsesBoundVersionsAndReportsFinalContributionsAndZeroAllocation() {
        var assessment=reviewed(new BigDecimal("50"),BigDecimal.ZERO,new BigDecimal("50"));
        var bound=assessment.getPlanVersions().get(0).getVersion();
        var newer=version(KpiLevel.COMPANY,99L);bound.getPlan().setCurrentVersion(newer);
        publications.put(bound.getId(),publication(bound,100L,4));
        assessment.setCheckpointScore(new BigDecimal("90"));
        var dto=service.breakdown(assessment);
        assertEquals(new BigDecimal("90"),dto.checkpointScore());
        assertEquals(0,new BigDecimal("40").compareTo(dto.levels().get(0).contribution()));
        assertEquals(com.tbm.careerpathlearning.dto.KpiScoreBreakdownDto.Availability.NOT_APPLICABLE,dto.levels().get(1).status());
        assertEquals(BigDecimal.ZERO,dto.levels().get(1).contribution());
        assertEquals(0,new BigDecimal("50").compareTo(dto.levels().get(2).contribution()));
        verify(central,never()).findByPlanVersionIdAndCheckpointIdAndStatus(eq(99L),anyLong(),any());
    }
}
