package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.tbm.careerpathlearning.dto.AnnualPerformanceHistoryDto.ComparisonStatus.*;
import static com.tbm.careerpathlearning.dto.AnnualPerformanceHistoryDto.SourceType.*;

/** Called after the appraisal service authorises access to the anchor participant. */
@Service
@Transactional(readOnly = true)
public class AnnualPerformanceHistoryService {
    private final ReviewPeriodParticipantRepository participants;
    private final AnnualAppraisalRepository appraisals;
    private final AnnualAppraisalActivityRepository activity;
    private final ReviewCheckpointRepository checkpoints;
    private final AnnualAppraisalReadinessService readiness;
    private final ObjectMapper json;

    public AnnualPerformanceHistoryService(ReviewPeriodParticipantRepository participants,
            AnnualAppraisalRepository appraisals, AnnualAppraisalActivityRepository activity,
            ReviewCheckpointRepository checkpoints, AnnualAppraisalReadinessService readiness, ObjectMapper json) {
        this.participants = participants;
        this.appraisals = appraisals;
        this.activity = activity;
        this.checkpoints = checkpoints;
        this.readiness = readiness;
        this.json = json;
    }

    public List<AnnualPerformanceHistoryDto> read(ReviewPeriodParticipant anchor) {
        var end = anchor.getReviewPeriod().getEndDate();
        var start = LocalDate.of(end.getYear() - 4, 1, 1);
        return participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(anchor.getStaff().getId()).stream()
                .filter(p -> Objects.equals(p.getStaff().getId(), anchor.getStaff().getId()))
                .filter(p -> !Objects.equals(p.getReviewPeriod().getId(), anchor.getReviewPeriod().getId()))
                .filter(p -> p.getReviewPeriod().getStatus() == AnnualKpiReviewPeriodStatus.OPEN
                        || p.getReviewPeriod().getStatus() == AnnualKpiReviewPeriodStatus.CLOSED)
                .filter(p -> !p.getReviewPeriod().getEndDate().isBefore(start)
                        && !p.getReviewPeriod().getEndDate().isAfter(end))
                .sorted(Comparator.comparing((ReviewPeriodParticipant p) -> p.getReviewPeriod().getEndDate())
                        .thenComparing(p -> p.getReviewPeriod().getId()))
                .map(this::result).flatMap(Optional::stream).toList();
    }

    private Optional<AnnualPerformanceHistoryDto> result(ReviewPeriodParticipant participant) {
        var appraisal = appraisals.findByParticipantId(participant.getId()).orElse(null);
        var recorded = validSubmission(appraisal, participant) ? appraisal.getSnapshot() : null;
        var current = readiness.evaluate(participant);
        boolean completed = current.ready() && validSummary(current.performance());
        if (recorded == null && !completed) return Optional.empty();
        var scores = recorded == null ? current.performance() : recorded;
        var comparison = recorded == null ? NOT_APPLICABLE : !completed ? CURRENT_RESULTS_UNAVAILABLE
                : sameScores(recorded, current.performance()) ? UNCHANGED : DIFFERENT;
        var period = participant.getReviewPeriod();
        return Optional.of(new AnnualPerformanceHistoryDto(participant.getStaff().getId(), participant.getId(),
                period.getId(), period.getName(), period.getStartDate(), period.getEndDate(), scores.annualKpiScore(),
                scores.attitudeScore(), scores.finalScore(), scores.kpiWeight(), scores.attitudeWeight(),
                scores.consolidationMethod(), recorded == null ? CALCULATED_RESULTS : SUBMISSION_SNAPSHOT, comparison));
    }

    private boolean validSubmission(AnnualAppraisal appraisal, ReviewPeriodParticipant participant) {
        if (appraisal == null || appraisal.getId() == null || appraisal.getSubmittedAt() == null || appraisal.getStatus() == null
                || !Set.of(AppraisalStatus.PENDING_REVIEW, AppraisalStatus.RETURNED, AppraisalStatus.APPROVED)
                        .contains(appraisal.getStatus())) return false;
        var captured = appraisal.getSnapshot();
        if (!validSummary(captured) || !Objects.equals(appraisal.getParticipant().getId(), participant.getId())
                || !Objects.equals(appraisal.getEmployeeId(), participant.getStaff().getId())
                || !Objects.equals(appraisal.getReviewPeriodId(), participant.getReviewPeriod().getId())
                || !Objects.equals(captured.participantId(), participant.getId())
                || !Objects.equals(captured.employeeId(), participant.getStaff().getId())
                || !Objects.equals(captured.reviewPeriodId(), participant.getReviewPeriod().getId())
                || captured.reviewFrequency() != participant.getReviewFrequency()
                || !validCheckpoints(captured, participant) || !validAttitude(captured.attitude())) return false;

        // A return/approval event is not proof of submission; never substitute an older submission.
        var submitted = activity.findAllByAppraisalIdOrderByIdAsc(appraisal.getId()).stream()
                .filter(e -> "SUBMITTED".equals(e.getAction()) || "RESUBMITTED".equals(e.getAction()))
                .max(Comparator.comparing(AnnualAppraisalActivity::getId));
        return submitted.filter(e -> Objects.equals(e.getAppraisalId(), appraisal.getId()))
                .filter(e -> e.getSnapshot() != null)
                .map(e -> equivalent(json.valueToTree(captured), json.valueToTree(e.getSnapshot()))).orElse(false);
    }

    private boolean validCheckpoints(AnnualAppraisalSnapshotDto captured, ReviewPeriodParticipant participant) {
        if (captured.reviewFrequency() == null || captured.checkpoints() == null) return false;
        var required = checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(
                participant.getReviewPeriod().getId(), participant.getReviewFrequency());
        if (required.isEmpty() || captured.checkpoints().size() != required.size()) return false;
        Map<Long, ReviewCheckpoint> schedule = new HashMap<>();
        required.forEach(c -> schedule.put(c.getId(), c));
        Set<Long> seen = new HashSet<>(), assessments = new HashSet<>();
        for (var c : captured.checkpoints()) {
            if (c == null || c.checkpointId() == null || !seen.add(c.checkpointId())
                    || c.assessmentId() == null || !assessments.add(c.assessmentId())
                    || c.status() != KpiAssessmentStatus.REVIEWED || !validScore(c.score())) return false;
            var expected = schedule.get(c.checkpointId());
            if (expected == null || !Objects.equals(expected.getSequenceNumber(), c.sequenceNumber())
                    || !Objects.equals(expected.getStartDate(), c.startDate())
                    || !Objects.equals(expected.getEndDate(), c.endDate())) return false;
        }
        return true;
    }

    private boolean validAttitude(AnnualAppraisalSnapshotDto.Attitude attitude) {
        if (attitude == null || attitude.assessmentId() == null || attitude.configurationId() == null
                || attitude.format() == null || attitude.status() != AttitudeAssessmentStatus.REVIEWED
                || attitude.criteria() == null || attitude.criteria().isEmpty()) return false;
        Set<Long> seen = new HashSet<>();
        // Use captured criteria, not today's configuration or mutable assessment answers.
        return attitude.criteria().stream().allMatch(c -> c != null && c.criterionId() != null
                && seen.add(c.criterionId()) && c.superiorPoint() != null && c.superiorPoint() >= 1
                && c.superiorPoint() <= 5);
    }

    private boolean validSummary(AnnualAppraisalSnapshotDto snapshot) {
        return snapshot != null && snapshot.consolidationMethod() != null
                && validScore(snapshot.annualKpiScore()) && validScore(snapshot.attitudeScore())
                && validScore(snapshot.finalScore()) && validScore(snapshot.kpiWeight())
                && validScore(snapshot.attitudeWeight())
                && snapshot.kpiWeight().add(snapshot.attitudeWeight()).compareTo(BigDecimal.valueOf(100)) == 0;
    }

    private boolean validScore(BigDecimal score) {
        return score != null && score.signum() >= 0 && score.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    private boolean sameScores(AnnualAppraisalSnapshotDto a, AnnualAppraisalSnapshotDto b) {
        return a.annualKpiScore().compareTo(b.annualKpiScore()) == 0
                && a.attitudeScore().compareTo(b.attitudeScore()) == 0 && a.finalScore().compareTo(b.finalScore()) == 0;
    }

    private boolean equivalent(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue()) == 0;
        if (a.isObject() && b.isObject() && a.size() == b.size()) {
            for (var field : a.properties()) {
                if (!b.has(field.getKey()) || !equivalent(field.getValue(), b.get(field.getKey()))) return false;
            }
            return true;
        }
        if (a.isArray() && b.isArray() && a.size() == b.size()) {
            for (int i = 0; i < a.size(); i++) if (!equivalent(a.get(i), b.get(i))) return false;
            return true;
        }
        return a.equals(b);
    }
}
