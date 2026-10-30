package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffPerformanceDto.Scores;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Pure aggregate calculations shared by Team and Organisation dashboards. */
final class PerformanceSummaryCalculator {
    private PerformanceSummaryCalculator() {}
    static Summary summary(List<ReviewPeriodParticipant> population, TeamPerformanceResults.Data data) {
        return new Summary(population.size(), average(population, data, Scores::finalScore).contributingEmployees(),
                average(population, data, Scores::annualKpiScore), average(population, data, Scores::attitudeScore), average(population, data, Scores::finalScore));
    }
    private static Average average(List<ReviewPeriodParticipant> population, TeamPerformanceResults.Data data, Function<Scores, BigDecimal> score) {
        var values = population.stream().map(p -> data.scores().get(p.getId())).filter(Objects::nonNull).map(score).filter(Objects::nonNull).toList();
        return new Average(values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP), values.size());
    }
    static Distribution distribution(List<ReviewPeriodParticipant> population, TeamPerformanceResults.Data data, Metric metric) {
        Function<Scores, BigDecimal> value = switch (metric) { case KPI -> Scores::annualKpiScore; case ATTITUDE -> Scores::attitudeScore; case FINAL -> Scores::finalScore; };
        int[] boundaries = {0, 60, 70, 80, 90, 100};
        int[] counts = new int[boundaries.length - 1]; int missing = 0;
        for (var p : population) {
            var score = value.apply(data.scores().get(p.getId()));
            if (score == null) missing++;
            else {
                int band = 0;
                // Compare the stored decimal directly; display rounding must not move employees between bands.
                while (band < counts.length - 1 && score.compareTo(BigDecimal.valueOf(boundaries[band + 1])) >= 0) band++;
                counts[band]++;
            }
        }
        var bins = new ArrayList<Bin>();
        for (int i = 0; i < counts.length; i++) bins.add(new Bin(boundaries[i], boundaries[i + 1], i == counts.length - 1, counts[i]));
        return new Distribution(metric, List.copyOf(bins), population.size() - missing, missing);
    }
    static Progress progress(AnnualKpiReviewPeriod period, List<ReviewPeriodParticipant> population, TeamPerformanceResults.Data data, LocalDate today) {
        boolean assessmentPeriod = period.getStatus() == AnnualKpiReviewPeriodStatus.OPEN || period.getStatus() == AnnualKpiReviewPeriodStatus.CLOSED;
        int kpiSelf = 0, kpiSuperior = 0, attitudeSelf = 0, attitudeSuperior = 0;
        var ids = new HashSet<Long>();
        for (var p : population) {
            ids.add(p.getId());
            for (var c : data.required(p)) {
                var a = data.assessment(p.getId(), c.getId());
                if (assessmentPeriod && c.getEndDate().isBefore(today) && (a == null || a.getStatus() == KpiAssessmentStatus.DRAFT)) kpiSelf++;
                if (a != null && a.getStatus() == KpiAssessmentStatus.PENDING_REVIEW) kpiSuperior++;
            }
            var attitude = data.attitudes().get(p.getId());
            if (assessmentPeriod && period.getAttitudeSelfAssessmentStartDate() != null && !today.isBefore(period.getAttitudeSelfAssessmentStartDate())
                    && (attitude == null || attitude.getStatus() == AttitudeAssessmentStatus.DRAFT)) attitudeSelf++;
            if (attitude != null && attitude.getStatus() == AttitudeAssessmentStatus.PENDING_REVIEW) attitudeSuperior++;
        }
        int returned = (int) data.plans().stream().filter(p -> ids.contains(p.getParticipantId()) && p.getStatus() == KpiPlanStatus.RETURNED).count();
        return new Progress(kpiSelf, kpiSuperior, attitudeSelf, attitudeSuperior, returned);
    }
    static Change employeeChange(ReviewPeriodParticipant current, ReviewPeriodParticipant prior, TeamPerformanceResults.Data data,
            Comparison comparison, AnnualKpiReviewPeriod priorPeriod) {
        if (comparison == Comparison.AVAILABLE && prior != null && (!validSignature(current) || !validSignature(prior) || !signature(current).equals(signature(prior))
                || !scheduleMatches(data.required(current), data.required(prior)))) comparison = Comparison.INCOMPATIBLE_CONFIGURATION;
        return change(data.scores().get(current.getId()).finalScore(), prior == null ? null : data.scores().get(prior.getId()).finalScore(), comparison, priorPeriod);
    }
    static Change scopeChange(List<ReviewPeriodParticipant> current, List<ReviewPeriodParticipant> prior,
            TeamPerformanceResults.Data data, Comparison comparison, AnnualKpiReviewPeriod previousPeriod) {
        if (comparison == Comparison.AVAILABLE && (current.stream().anyMatch(p -> !validSignature(p)) || prior.stream().anyMatch(p -> !validSignature(p))
                || !signatures(current).equals(signatures(prior)) || !comparableSchedules(current, prior, data))) comparison = Comparison.INCOMPATIBLE_CONFIGURATION;
        return change(summary(current, data).finalScore().score(), summary(prior, data).finalScore().score(), comparison, previousPeriod);
    }
    static Change change(BigDecimal current, BigDecimal before, Comparison comparison, AnnualKpiReviewPeriod prior) {
        if (comparison == Comparison.AVAILABLE && (current == null || before == null)) comparison = Comparison.NO_COMPARABLE_RESULTS;
        return new Change(comparison == Comparison.AVAILABLE ? current.subtract(before).setScale(4, RoundingMode.HALF_UP) : null,
                comparison, prior == null ? null : prior.getId());
    }
    static boolean comparablePeriod(AnnualKpiReviewPeriod current, AnnualKpiReviewPeriod prior) {
        return current.getStartDate().minusYears(1).equals(prior.getStartDate()) && current.getEndDate().minusYears(1).equals(prior.getEndDate())
                && current.getFrequencyPolicy() == prior.getFrequencyPolicy() && current.getAssessmentModel() == prior.getAssessmentModel()
                && current.getAnnualKpiConsolidationMethod() != null && current.getAnnualKpiConsolidationMethod() == prior.getAnnualKpiConsolidationMethod()
                && AnnualPerformanceCalculation.validWeights(current.getKpiPerformanceWeight(), current.getAttitudeEvaluationWeight())
                && same(current.getKpiPerformanceWeight(), prior.getKpiPerformanceWeight()) && same(current.getAttitudeEvaluationWeight(), prior.getAttitudeEvaluationWeight());
    }
    private static boolean same(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private record Signature(ReviewFrequency frequency, BigDecimal company, BigDecimal department, BigDecimal individual) {}
    private static Signature signature(ReviewPeriodParticipant p) {
        var w = p.getEmployeeLevelConfiguration();
        return new Signature(p.getReviewFrequency(), normal(w == null ? null : w.getCompanyKpiWeight()),
                normal(w == null ? null : w.getDepartmentKpiWeight()), normal(w == null ? null : w.getIndividualKpiWeight()));
    }
    private static BigDecimal normal(BigDecimal n) { return n == null ? null : n.stripTrailingZeros(); }
    private static boolean validSignature(ReviewPeriodParticipant p) {
        var s = signature(p);var weights = Arrays.asList(s.company(), s.department(), s.individual());
        return s.frequency() != null && weights.stream().allMatch(w -> AnnualPerformanceCalculation.validScore(w) && w.stripTrailingZeros().scale() <= 2)
                && weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("100")) == 0;
    }
    private static Set<Signature> signatures(List<ReviewPeriodParticipant> population) {
        var values = new HashSet<Signature>(); population.forEach(p -> values.add(signature(p))); return values;
    }
    private static boolean comparableSchedules(List<ReviewPeriodParticipant> current, List<ReviewPeriodParticipant> prior, TeamPerformanceResults.Data data) {
        var beforeByFrequency = new EnumMap<ReviewFrequency, ReviewPeriodParticipant>(ReviewFrequency.class);
        prior.forEach(p -> beforeByFrequency.putIfAbsent(p.getReviewFrequency(), p));
        for (var p : current) {
            var before = beforeByFrequency.get(p.getReviewFrequency());
            if (before == null || !scheduleMatches(data.required(p), data.required(before))) return false;
        }
        return true;
    }
    private static boolean scheduleMatches(List<ReviewCheckpoint> current, List<ReviewCheckpoint> prior) {
        if (current.isEmpty() || current.size() != prior.size()) return false;
        for (int i = 0; i < current.size(); i++) if (!current.get(i).getStartDate().minusYears(1).equals(prior.get(i).getStartDate())
                || !current.get(i).getEndDate().minusYears(1).equals(prior.get(i).getEndDate())) return false;
        return true;
    }
}
