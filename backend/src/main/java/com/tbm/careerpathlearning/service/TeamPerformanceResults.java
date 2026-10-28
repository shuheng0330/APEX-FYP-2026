package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffPerformanceDto.Scores;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.TeamPerformanceRepository;
import com.tbm.careerpathlearning.repository.ReviewCheckpointRepository;
import com.tbm.careerpathlearning.repository.KpiAssessmentRepository;
import com.tbm.careerpathlearning.repository.AttitudeAssessmentRepository;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static com.tbm.careerpathlearning.service.AnnualPerformanceCalculation.*;

/** Batch counterpart to readiness: uses stored checkpoint scores, never refreshes aggregates. */
@Component
public class TeamPerformanceResults {
    private final TeamPerformanceRepository repository;
    private final ReviewCheckpointRepository checkpointRepository;
    private final KpiAssessmentRepository kpiRepository;
    private final AttitudeAssessmentRepository attitudeRepository;
    public TeamPerformanceResults(TeamPerformanceRepository repository, ReviewCheckpointRepository checkpointRepository,
            KpiAssessmentRepository kpiRepository, AttitudeAssessmentRepository attitudeRepository) {
        this.repository = repository; this.checkpointRepository = checkpointRepository;
        this.kpiRepository = kpiRepository; this.attitudeRepository = attitudeRepository;
    }

    record Schedule(Long periodId, ReviewFrequency frequency) {}
    record AssessmentKey(Long participantId, Long checkpointId) {}
    record Source(Long versionId, Long checkpointId) {}
    record Data(Map<Long, Scores> scores, Map<Schedule, List<ReviewCheckpoint>> schedules,
            Map<AssessmentKey, KpiAssessment> assessments, Map<Long, AttitudeAssessment> attitudes,
            List<TeamPerformanceRepository.IndividualPlan> plans,
            List<TeamPerformanceRepository.ReturnedRevision> returnedRevisions) {
        List<ReviewCheckpoint> required(ReviewPeriodParticipant participant) {
            return schedules.getOrDefault(new Schedule(participant.getReviewPeriod().getId(), participant.getReviewFrequency()), List.of());
        }
        KpiAssessment assessment(Long participantId, Long checkpointId) {
            return assessments.get(new AssessmentKey(participantId, checkpointId));
        }
    }

    Data load(List<ReviewPeriodParticipant> population) {
        if (population.isEmpty()) return new Data(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of());
        var ids = population.stream().map(ReviewPeriodParticipant::getId).toList();
        var periodIds = population.stream().map(p -> p.getReviewPeriod().getId()).distinct().toList();
        var schedules = new HashMap<Schedule, List<ReviewCheckpoint>>();
        var checkpoints = checkpointRepository.findPerformanceCheckpoints(periodIds).stream().sorted(Comparator.comparing(ReviewCheckpoint::getEndDate)
                .thenComparing(ReviewCheckpoint::getSequenceNumber).thenComparing(ReviewCheckpoint::getId)).toList();
        for (var c : checkpoints) schedules.computeIfAbsent(new Schedule(c.getReviewPeriod().getId(), c.getReviewFrequency()), k -> new ArrayList<>()).add(c);
        var assessments = new HashMap<AssessmentKey, KpiAssessment>();
        for (var a : batches(ids, kpiRepository::findPerformanceAssessments))
            assessments.put(new AssessmentKey(a.getParticipant().getId(), a.getCheckpoint().getId()), a);
        var attitudes = new HashMap<Long, AttitudeAssessment>();
        for (var a : batches(ids, attitudeRepository::findPerformanceAssessments)) attitudes.put(a.getParticipant().getId(), a);
        var fallback = new HashMap<Long, Map<KpiLevel, Long>>();
        var periodsByParticipant = new HashMap<Long, Long>();
        population.forEach(p -> periodsByParticipant.put(p.getId(), p.getReviewPeriod().getId()));
        for (var v : batches(ids, repository::findFallbackVersions)) {
            if (!Objects.equals(v.getPeriodId(), periodsByParticipant.get(v.getParticipantId()))) continue;
            // Ambiguous fallback versions are unavailable, never chosen arbitrarily.
            fallback.computeIfAbsent(v.getParticipantId(), k -> new EnumMap<>(KpiLevel.class))
                    .merge(v.getLevel(), v.getVersionId(), (a, b) -> a.equals(b) ? a : -1L);
        }
        var published = new HashSet<Source>();
        for (var s : repository.findPublishedSources(periodIds)) published.add(new Source(s.getVersionId(), s.getCheckpointId()));
        var scores = new HashMap<Long, Scores>();
        var data = new Data(scores, schedules, assessments, attitudes, batches(ids, repository::findIndividualPlans),
                batches(ids, repository::findReturnedRevisions));
        for (var p : population) {
            var period = p.getReviewPeriod();
            BigDecimal kpi = null, attitude = null;
            if (period.getStatus() == AnnualKpiReviewPeriodStatus.OPEN || period.getStatus() == AnnualKpiReviewPeriodStatus.CLOSED) {
                var ordered = new ArrayList<BigDecimal>();
                for (var c : data.required(p)) {
                    var a = data.assessment(p.getId(), c.getId());
                    ordered.add(a != null && a.getStatus() == KpiAssessmentStatus.REVIEWED
                            && Objects.equals(a.getReviewPeriodId(), period.getId()) && a.getReviewFrequency() == p.getReviewFrequency()
                            && (a.getAssessmentModel() != KpiAssessmentModel.CENTRAL_RESULTS
                                || centralAvailable(p, a, schedules, fallback.getOrDefault(p.getId(), Map.of()), published))
                            ? a.getCheckpointScore() : null);
                }
                kpi = annualKpi(ordered, period.getAnnualKpiConsolidationMethod());
                var a = attitudes.get(p.getId());
                if (a != null && Objects.equals(a.getReviewPeriodId(), period.getId())
                        && a.getStatus() == AttitudeAssessmentStatus.REVIEWED && validScore(a.getAttitudeScore())) attitude = a.getAttitudeScore();
            }
            scores.put(p.getId(), new Scores(kpi, attitude, finalScore(kpi, attitude, period.getKpiPerformanceWeight(), period.getAttitudeEvaluationWeight()),
                    period.getKpiPerformanceWeight(), period.getAttitudeEvaluationWeight(), period.getAnnualKpiConsolidationMethod()));
        }
        return data;
    }

    private boolean centralAvailable(ReviewPeriodParticipant p, KpiAssessment assessment,
            Map<Schedule, List<ReviewCheckpoint>> schedules, Map<KpiLevel, Long> fallback, Set<Source> published) {
        var allocation = p.getEmployeeLevelConfiguration();
        if (allocation == null) return false;
        var weights = Arrays.asList(allocation.getCompanyKpiWeight(), allocation.getDepartmentKpiWeight(), allocation.getIndividualKpiWeight());
        if (weights.stream().anyMatch(w -> !validScore(w) || w.stripTrailingZeros().scale() > 2)
                || weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("100")) != 0) return false;
        var versions = new EnumMap<KpiLevel, Long>(KpiLevel.class);
        versions.putAll(fallback);
        assessment.getPlanVersions().forEach(v -> versions.put(v.getLevel(), v.getVersion().getId()));
        for (var level : List.of(KpiLevel.COMPANY, KpiLevel.DEPARTMENT)) {
            var weight = level == KpiLevel.COMPANY ? allocation.getCompanyKpiWeight() : allocation.getDepartmentKpiWeight();
            if (weight.signum() == 0) continue;
            Long checkpointId = assessment.getCheckpoint().getId();
            if (level == KpiLevel.COMPANY && p.getReviewPeriod().getFrequencyPolicy() == KpiFrequencyPolicy.DEPARTMENT_BASED) {
                var monthly = schedules.getOrDefault(new Schedule(p.getReviewPeriod().getId(), ReviewFrequency.MONTHLY), List.of()).stream()
                        .filter(c -> c.getEndDate().equals(assessment.getCheckpoint().getEndDate())).toList();
                if (monthly.size() != 1) return false;
                checkpointId = monthly.get(0).getId();
            }
            if (!published.contains(new Source(versions.get(level), checkpointId))) return false;
        }
        return true;
    }

    private <T> List<T> batches(List<Long> ids, Function<Collection<Long>, List<T>> read) {
        var result = new ArrayList<T>();
        for (int i = 0; i < ids.size(); i += 500) result.addAll(read.apply(ids.subList(i, Math.min(ids.size(), i + 500))));
        return result;
    }
}
