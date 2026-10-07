package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.service.AnnualReviewPeriodConfigurationValidator;
import com.tbm.careerpathlearning.service.ReviewCheckpointGenerator;
import com.tbm.careerpathlearning.service.ReviewPeriodParticipantFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AnnualReviewPeriodFoundationTest {
    private final ReviewCheckpointGenerator generator = new ReviewCheckpointGenerator();
    private final AnnualReviewPeriodConfigurationValidator validator = new AnnualReviewPeriodConfigurationValidator();

    @Test
    void monthlyCheckpointsIncludeJanuaryDeadlinesForDecember() {
        var period = period();
        var checkpoints = generator.generate(period, ReviewFrequency.MONTHLY);
        assertThat(checkpoints).hasSize(12);
        assertThat(checkpoints.get(0).getEndDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(checkpoints.get(11).getSequenceNumber()).isEqualTo(12);
        assertThat(checkpoints.get(11).getSelfAssessmentDeadline()).isEqualTo(LocalDate.of(2028, 1, 5));
        assertThat(checkpoints.get(11).getSuperiorAssessmentDeadline()).isEqualTo(LocalDate.of(2028, 1, 10));
        assertThat(period.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.DRAFT);
    }

    @Test
    void quarterlyAndAnnualCheckpointsUseTheirOwnFrequency() {
        var quarterly = generator.generate(period(), ReviewFrequency.QUARTERLY);
        assertThat(quarterly).hasSize(4);
        assertThat(quarterly).extracting(ReviewCheckpoint::getEndDate).containsExactly(
                LocalDate.of(2027, 3, 31), LocalDate.of(2027, 6, 30),
                LocalDate.of(2027, 9, 30), LocalDate.of(2027, 12, 31));
        var annual = generator.generate(period(), ReviewFrequency.ANNUALLY);
        assertThat(annual).hasSize(1);
        assertThat(annual.get(0).getStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(annual.get(0).getEndDate()).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    void partialAndLeapYearMonthsRemainWithinMeasurementDates() {
        var period = period();
        period.setStartDate(LocalDate.of(2028, 2, 15));
        period.setEndDate(LocalDate.of(2028, 4, 10));
        var checkpoints = generator.generate(period, ReviewFrequency.MONTHLY);
        assertThat(checkpoints).hasSize(3);
        assertThat(checkpoints.get(0).getEndDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(checkpoints.get(2).getEndDate()).isEqualTo(period.getEndDate());
        assertThat(checkpoints.get(2).getSelfAssessmentDeadline()).isEqualTo(LocalDate.of(2028, 4, 15));
    }

    @Test
    void invalidDatesAndNonPositiveOffsetsAreRejected() {
        var period = period();
        period.setEndDate(period.getStartDate());
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(period, ReviewFrequency.MONTHLY));
        period.setEndDate(LocalDate.of(2027, 12, 31));
        period.setSelfAssessmentDaysAfterCheckpoint(0);
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(period, ReviewFrequency.MONTHLY));
        period.setSelfAssessmentDaysAfterCheckpoint(5);
        period.setSuperiorAssessmentDaysAfterSelfDeadline(null);
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(period, ReviewFrequency.MONTHLY));
    }

    @Test
    void draftsMayBeIncompleteButScheduledWeightagesMustTotalOneHundred() {
        assertThatCode(() -> validator.validateDraft(new AnnualKpiReviewPeriod())).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(
                () -> validator.validateForScheduling(new AnnualKpiReviewPeriod()));
        var period = period();
        assertThatCode(() -> validator.validateForScheduling(period)).doesNotThrowAnyException();
        var weight = new ReviewPeriodEmployeeLevelConfiguration();
        weight.setCompanyKpiWeight(new BigDecimal("15"));
        weight.setDepartmentKpiWeight(new BigDecimal("25"));
        weight.setIndividualKpiWeight(new BigDecimal("59"));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateLevelWeights(weight, true));
        period.setAttitudeEvaluationWeight(new BigDecimal("49.00"));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateForScheduling(period));
    }

    @Test
    void invalidWeightRangeAndPrecisionAreRejected() {
        var weight = new ReviewPeriodEmployeeLevelConfiguration();
        weight.setCompanyKpiWeight(new BigDecimal("-1"));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateLevelWeights(weight, false));
        weight.setCompanyKpiWeight(new BigDecimal("100.01"));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateLevelWeights(weight, false));
        weight.setCompanyKpiWeight(new BigDecimal("15.001"));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateLevelWeights(weight, false));
    }

    @Test
    void deadlineDatesCanExtendIntoTheFollowingYear() {
        var period = period();
        period.setAppraisalRecommendationDeadline(LocalDate.of(2028, 1, 15));
        period.setHrFinalisationDeadline(LocalDate.of(2028, 1, 25));
        assertThatCode(() -> validator.validateForScheduling(period)).doesNotThrowAnyException();
        period.setKpiSetupDeadline(LocalDate.of(2027, 1, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateForScheduling(period));
    }

    @Test
    void roleDefaultIsOptionalAndPeriodOverrideDoesNotChangeIt() {
        Role role = new Role();
        assertThat(ReviewPeriodRoleConfiguration.resolveFrequency(role, null)).isEqualTo(ReviewFrequency.ANNUALLY);
        role.setDefaultReviewFrequency(ReviewFrequency.QUARTERLY);
        assertThat(ReviewPeriodRoleConfiguration.resolveFrequency(role, null)).isEqualTo(ReviewFrequency.QUARTERLY);
        assertThat(ReviewPeriodRoleConfiguration.resolveFrequency(role, ReviewFrequency.MONTHLY))
                .isEqualTo(ReviewFrequency.MONTHLY);
        assertThat(role.getDefaultReviewFrequency()).isEqualTo(ReviewFrequency.QUARTERLY);
    }

    @Test
    void participantLabelsAndFrequencyDoNotFollowLaterOrganisationChanges() {
        var period = period();
        var role = new Role(); role.setName("Retail Sales Executive");
        var department = new OrgChart(); department.setType(OrgChartType.D); department.setName("Retail Sales");
        var superior = new Staff(); superior.setName("Sales Manager");
        var staff = new Staff(); staff.setId(UUID.randomUUID()); staff.setName("Amir");
        staff.setRole(role); staff.setManager(superior);
        var configuration = new ReviewPeriodRoleConfiguration();
        configuration.setReviewPeriod(period); configuration.setRole(role);
        configuration.setReviewFrequency(ReviewFrequency.MONTHLY);
        var weight = new ReviewPeriodEmployeeLevelConfiguration();
        weight.setId(1L); weight.setReviewPeriod(period);
        configuration.setEmployeeLevelConfiguration(weight); configuration.setEmployeeLevelConfigurationId(1L);
        var participant = new ReviewPeriodParticipantFactory().snapshot(period, staff, configuration, department);
        role.setName("New Role"); department.setName("New Department"); superior.setName("New Superior");
        configuration.setReviewFrequency(ReviewFrequency.ANNUALLY);
        assertThat(participant.getRoleName()).isEqualTo("Retail Sales Executive");
        assertThat(participant.getDepartmentName()).isEqualTo("Retail Sales");
        assertThat(participant.getSuperiorName()).isEqualTo("Sales Manager");
        assertThat(participant.getReviewFrequency()).isEqualTo(ReviewFrequency.MONTHLY);
    }

    @Test
    void participantCannotUseAnotherPeriodsConfiguration() {
        var period = period(); var role = new Role(); var staff = new Staff(); staff.setRole(role);
        var configuration = new ReviewPeriodRoleConfiguration(); configuration.setRole(role);
        configuration.setReviewPeriod(period()); configuration.setReviewFrequency(ReviewFrequency.MONTHLY);
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ReviewPeriodParticipantFactory().snapshot(period, staff, configuration, null));
    }

    @Test
    void systemRolesCannotBecomePerformanceReviewParticipants() {
        var period = period(); var role = new Role(); var staff = new Staff(); staff.setRole(role);
        role.setPerformanceReviewEligible(false);
        var configuration = new ReviewPeriodRoleConfiguration(); configuration.setRole(role);
        configuration.setReviewPeriod(period); configuration.setReviewFrequency(ReviewFrequency.MONTHLY);
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ReviewPeriodParticipantFactory().snapshot(period, staff, configuration, null))
                .withMessageContaining("do not participate");
    }

    private AnnualKpiReviewPeriod period() {
        var period = new AnnualKpiReviewPeriod();
        period.setName("2027 Annual KPI Review");
        period.setStartDate(LocalDate.of(2027, 1, 1)); period.setEndDate(LocalDate.of(2027, 12, 31));
        period.setSelfAssessmentDaysAfterCheckpoint(5); period.setSuperiorAssessmentDaysAfterSelfDeadline(5);
        period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        return period;
    }
}
