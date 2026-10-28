package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.dto.StaffPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_VIEW_TEAM_PERFORMANCE','CAN_VIEW_DEPARTMENT_PERFORMANCE','CAN_VIEW_ORGANISATION_PERFORMANCE')")
public class StaffPerformanceService {
    private final ReviewPeriodParticipantRepository participants;
    private final StaffRepository staff;
    private final KpiAssessmentRepository assessments;
    private final ReviewCheckpointRepository checkpoints;
    private final AttitudeAssessmentRepository attitudes;
    private final AnnualAppraisalRepository appraisals;
    private final AnnualAppraisalReadinessService readiness;
    private final KpiCheckpointAggregateService aggregates;
    private final StaffPerformanceAccess access;
    private final StaffPerformanceMapper mapper;
    private final KpiPlanMapper kpis;

    public StaffPerformanceService(ReviewPeriodParticipantRepository participants, StaffRepository staff,
            KpiAssessmentRepository assessments, ReviewCheckpointRepository checkpoints,
            AttitudeAssessmentRepository attitudes, AnnualAppraisalRepository appraisals,
            AnnualAppraisalReadinessService readiness, KpiCheckpointAggregateService aggregates,
            StaffPerformanceAccess access, StaffPerformanceMapper mapper, KpiPlanMapper kpis) {
        this.participants = participants;
        this.staff = staff;
        this.assessments = assessments;
        this.checkpoints = checkpoints;
        this.attitudes = attitudes;
        this.appraisals = appraisals;
        this.readiness = readiness;
        this.aggregates = aggregates;
        this.access = access;
        this.mapper = mapper;
        this.kpis = kpis;
    }

    public List<Period> periods(UUID employeeId, UUID actorId) {
        var actor = access.actor(actorId);
        return authorisedPeriods(employeeId, actor).stream().map(mapper::period).toList();
    }

    public StaffPerformanceDto dashboard(UUID employeeId, Long periodId, int historyYears, UUID actorId) {
        if (historyYears != 3 && historyYears != 5) throw new BadRequestException("Select a 3 or 5 year trend");
        var actor = access.actor(actorId);
        var available = authorisedPeriods(employeeId, actor);
        var selected = available.stream().filter(p -> Objects.equals(p.getReviewPeriod().getId(), periodId))
                .findFirst().orElseThrow(StaffPerformanceAccess::denied);
        var selectedScores = scores(selected);
        var end = selected.getReviewPeriod().getEndDate();
        var start = LocalDate.of(end.getYear() - historyYears + 1, 1, 1);
        var scoreCache = new HashMap<Long, Scores>();
        scoreCache.put(selected.getId(), selectedScores);
        var trend = available.stream().filter(p -> completedPeriod(p)
                        && !p.getReviewPeriod().getEndDate().isBefore(start)
                        && !p.getReviewPeriod().getEndDate().isAfter(end))
                .sorted(Comparator.comparing((ReviewPeriodParticipant p) -> p.getReviewPeriod().getEndDate())
                        .thenComparing(p -> p.getReviewPeriod().getId()))
                .map(p -> new TrendPoint(mapper.period(p), scoreCache.computeIfAbsent(p.getId(), id -> scores(p)),
                        SourceType.LATEST_COMPLETED_RESULTS))
                .filter(t -> hasScore(t.scores())).toList();
        var approved = appraisals.findAllByEmployeeIdOrderBySubmittedAtDescIdDesc(employeeId).stream()
                .filter(this::approved).filter(a -> published(a.getParticipant()))
                .filter(a -> access.participant(a.getParticipant(), actor))
                .filter(a -> canViewApproval(a.getParticipant(), actor))
                .filter(a -> !a.getParticipant().getReviewPeriod().getEndDate().isAfter(end))
                .sorted(Comparator.comparing((AnnualAppraisal a) -> a.getParticipant().getReviewPeriod().getEndDate())
                        .thenComparing(AnnualAppraisal::getReviewPeriodId).reversed())
                .toList();
        var selectedAppraisal = approved.stream().filter(a -> Objects.equals(a.getParticipant().getId(), selected.getId()))
                .findFirst().map(a -> approval(a, actor, selectedScores)).orElse(null);
        var history = approved.stream().filter(a -> !Objects.equals(a.getParticipant().getId(), selected.getId()))
                .map(a -> approval(a, actor, scoreCache.computeIfAbsent(a.getParticipant().getId(), id -> scores(a.getParticipant()))))
                .toList();
        var checkpointRows = checkpointHistory(selected, actor);
        return new StaffPerformanceDto(mapper.employee(selected), mapper.period(selected), selectedScores,
                checkpointRows.isEmpty() ? null : checkpointRows.get(checkpointRows.size() - 1).id(),
                checkpointRows, attitude(selected, actor), selectedAppraisal, history, trend,
                new Capabilities(employeeId.equals(actorId), access.internalAppraisal(selected, actor)));
    }

    public CheckpointDetails checkpoint(Long participantId, Long checkpointId, UUID actorId) {
        var actor = access.actor(actorId);
        var participant = authorisedParticipant(participantId, actor);
        var checkpoint = requiredCheckpoints(participant).stream().filter(c -> Objects.equals(c.getId(), checkpointId))
                .findFirst().orElseThrow(StaffPerformanceAccess::denied);
        var assessment = assessments.findAllByParticipantId(participantId).stream()
                .filter(a -> Objects.equals(a.getCheckpoint().getId(), checkpointId)).findFirst().orElse(null);
        var visible = visibleAssessment(assessment, participant, actor);
        var answers = visible == null ? List.<KpiAnswer>of() : visible.getItems().stream()
                .sorted(Comparator.comparing(KpiAssessmentItem::getId))
                .map(i -> new KpiAnswer(i.getId(), i.getAssignment().getKpi().getPlan().getLevel(),
                        kpis.toDto(i.getAssignment().getKpi()), i.getSelfPoint(), i.getSelfComment(),
                        reviewed(visible) ? i.getSuperiorPoint() : null,
                        reviewed(visible) ? i.getSuperiorComment() : null)).toList();
        KpiScoreBreakdownDto breakdown = null;
        if (visible != null) {
            try {
                if (visible.getAssessmentModel() == KpiAssessmentModel.CENTRAL_RESULTS) {
                    var source = aggregates.breakdown(visible);
                    breakdown = new KpiScoreBreakdownDto(source.assessmentId(),
                            reviewed(visible) ? valid(source.checkpointScore()) : null, source.levels());
                } else if (reviewed(visible)) {
                    breakdown = readiness.evaluate(participant).performance().checkpoints().stream()
                            .filter(c -> Objects.equals(c.checkpointId(), checkpointId))
                            .map(AnnualAppraisalSnapshotDto.Checkpoint::breakdown).findFirst().orElse(null);
                }
            } catch (BadRequestException unavailable) {
                // A missing historical source is shown as unavailable; GET never repairs it.
                breakdown = null;
            }
        }
        return new CheckpointDetails(mapper.period(participant), checkpoint(checkpoint, visible),
                visible == null ? participant.getReviewPeriod().getAssessmentModel() : visible.getAssessmentModel(),
                breakdown, answers);
    }

    public Attitude attitude(Long participantId, UUID actorId) {
        var actor = access.actor(actorId);
        return attitude(authorisedParticipant(participantId, actor), actor);
    }

    public ApprovedAppraisal appraisal(Long participantId, Long appraisalId, UUID actorId) {
        var actor = access.actor(actorId);
        var participant = authorisedParticipant(participantId, actor);
        if (!canViewApproval(participant, actor)) throw StaffPerformanceAccess.denied();
        var appraisal = appraisals.findById(appraisalId).orElseThrow(StaffPerformanceAccess::denied);
        if (!approved(appraisal) || !Objects.equals(appraisal.getParticipant().getId(), participantId)
                || !Objects.equals(appraisal.getEmployeeId(), participant.getStaff().getId())) throw StaffPerformanceAccess.denied();
        return approval(appraisal, actor, scores(participant));
    }

    private List<ReviewPeriodParticipant> authorisedPeriods(UUID employeeId, Staff actor) {
        var employee = staff.findById(employeeId).orElseThrow(StaffPerformanceAccess::denied);
        access.employee(employee, actor);
        return participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employeeId).stream()
                .filter(p -> Objects.equals(p.getStaff().getId(), employeeId))
                .filter(this::published).filter(p -> access.participant(p, actor)).toList();
    }

    private ReviewPeriodParticipant authorisedParticipant(Long id, Staff actor) {
        var participant = participants.findById(id).orElseThrow(StaffPerformanceAccess::denied);
        if (!published(participant) || !access.participant(participant, actor)) throw StaffPerformanceAccess.denied();
        return participant;
    }

    private boolean published(ReviewPeriodParticipant p) {
        return p.getReviewPeriod().getStatus() != AnnualKpiReviewPeriodStatus.DRAFT;
    }

    private boolean completedPeriod(ReviewPeriodParticipant p) {
        return p.getReviewPeriod().getStatus() == AnnualKpiReviewPeriodStatus.OPEN
                || p.getReviewPeriod().getStatus() == AnnualKpiReviewPeriodStatus.CLOSED;
    }

    private Scores scores(ReviewPeriodParticipant p) {
        var period = p.getReviewPeriod();
        if (completedPeriod(p)) {
            try {
                var source = readiness.evaluate(p).performance();
                return new Scores(valid(source.annualKpiScore()), valid(source.attitudeScore()), valid(source.finalScore()),
                        source.kpiWeight(), source.attitudeWeight(), source.consolidationMethod());
            } catch (BadRequestException unavailable) {
                // Attitude remains independently available even if the KPI source cannot be read.
            }
        }
        var attitude = completedPeriod(p) ? attitudes.findByParticipantId(p.getId()).orElse(null) : null;
        return new Scores(null, attitude != null && attitude.getStatus() == AttitudeAssessmentStatus.REVIEWED
                ? valid(attitude.getAttitudeScore()) : null, null, period.getKpiPerformanceWeight(),
                period.getAttitudeEvaluationWeight(), period.getAnnualKpiConsolidationMethod());
    }

    private List<Checkpoint> checkpointHistory(ReviewPeriodParticipant p, Staff actor) {
        var byCheckpoint = new HashMap<Long, KpiAssessment>();
        assessments.findAllByParticipantId(p.getId()).forEach(a -> byCheckpoint.put(a.getCheckpoint().getId(), a));
        return requiredCheckpoints(p).stream().map(c -> checkpoint(c,
                visibleAssessment(byCheckpoint.get(c.getId()), p, actor))).toList();
    }

    private List<ReviewCheckpoint> requiredCheckpoints(ReviewPeriodParticipant p) {
        return checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(
                        p.getReviewPeriod().getId(), p.getReviewFrequency()).stream()
                .sorted(Comparator.comparing(ReviewCheckpoint::getEndDate)
                        .thenComparing(ReviewCheckpoint::getSequenceNumber).thenComparing(ReviewCheckpoint::getId)).toList();
    }

    private KpiAssessment visibleAssessment(KpiAssessment a, ReviewPeriodParticipant p, Staff actor) {
        return a != null && (a.getStatus() != KpiAssessmentStatus.DRAFT || p.getStaff().getId().equals(actor.getId())) ? a : null;
    }

    private boolean reviewed(KpiAssessment a) { return a.getStatus() == KpiAssessmentStatus.REVIEWED; }

    private Checkpoint checkpoint(ReviewCheckpoint c, KpiAssessment a) {
        return new Checkpoint(c.getId(), c.getSequenceNumber(), c.getStartDate(), c.getEndDate(), a == null ? null : a.getId(),
                a == null ? null : a.getStatus(), a != null && reviewed(a) ? valid(a.getCheckpointScore()) : null,
                a == null ? null : a.getSubmittedAt(), a != null && reviewed(a) ? a.getReviewedAt() : null);
    }

    private Attitude attitude(ReviewPeriodParticipant p, Staff actor) {
        var a = attitudes.findByParticipantId(p.getId()).orElse(null);
        if (a == null || a.getStatus() == AttitudeAssessmentStatus.DRAFT && !p.getStaff().getId().equals(actor.getId()))
            return new Attitude(null, null, null, null, null, null, null, null, List.of(), List.of());
        boolean reviewed = a.getStatus() == AttitudeAssessmentStatus.REVIEWED;
        var criteria = a.getItems().stream()
                .sorted(Comparator.comparing((AttitudeAssessmentItem i) -> i.getCriterion().getDisplayOrder())
                        .thenComparing(i -> i.getCriterion().getId()))
                .map(i -> new AttitudeCriterionResult(i.getCriterion().getId(), i.getCriterion().getName(),
                        i.getCriterion().getDescription(), i.getCriterion().getCriterionType(),
                        i.getSelfPoint(), i.getSelfComment(), reviewed ? i.getSuperiorPoint() : null,
                        reviewed ? i.getSuperiorComment() : null)).toList();
        var ratings = a.getConfiguration().getRatingDefinitions().stream()
                .sorted(Comparator.comparing(AttitudeRatingDefinition::getPoint))
                .map(r -> new Rating(r.getPoint(), r.getLabel(), r.getDescription())).toList();
        return new Attitude(a.getId(), a.getConfiguration().getId(), a.getConfiguration().getName(),
                a.getEvaluationFormat(), a.getStatus(), reviewed ? valid(a.getAttitudeScore()) : null,
                a.getSubmittedAt(), reviewed ? a.getReviewedAt() : null, criteria, ratings);
    }

    private boolean approved(AnnualAppraisal a) {
        return a.getStatus() == AppraisalStatus.APPROVED && a.getFinalDecision() != null && a.getReviewedAt() != null;
    }

    private boolean canViewApproval(ReviewPeriodParticipant participant, Staff actor) {
        return participant.getStaff().getId().equals(actor.getId()) || access.internalAppraisal(participant, actor);
    }

    private ApprovedAppraisal approval(AnnualAppraisal a, Staff actor, Scores current) {
        var captured = a.getSnapshot();
        var recorded = captured == null ? null : new Scores(valid(captured.annualKpiScore()), valid(captured.attitudeScore()),
                valid(captured.finalScore()), captured.kpiWeight(), captured.attitudeWeight(), captured.consolidationMethod());
        boolean internal = access.internalAppraisal(a.getParticipant(), actor);
        return new ApprovedAppraisal(a.getId(), mapper.period(a.getParticipant()), AppraisalStatus.APPROVED,
                a.getFinalDecision(), a.getReviewedAt(), recorded, comparison(recorded, current),
                internal ? new InternalAppraisal(a.getRecommendation(), a.getJustification(), a.getOverrideReason(),
                        a.getReviewedLate(), captured) : null);
    }

    private RecordedComparison comparison(Scores recorded, Scores current) {
        if (recorded == null) return RecordedComparison.NOT_APPLICABLE;
        if (current.annualKpiScore() == null || current.attitudeScore() == null || current.finalScore() == null)
            return RecordedComparison.CURRENT_RESULTS_UNAVAILABLE;
        if (same(recorded.annualKpiScore(), current.annualKpiScore()) && same(recorded.attitudeScore(), current.attitudeScore())
                && same(recorded.finalScore(), current.finalScore())) return RecordedComparison.UNCHANGED;
        return RecordedComparison.DIFFERENT;
    }

    private boolean same(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private BigDecimal valid(BigDecimal score) {
        return score != null && score.signum() >= 0 && score.compareTo(BigDecimal.valueOf(100)) <= 0 ? score : null;
    }
    private boolean hasScore(Scores scores) {
        return scores.annualKpiScore() != null || scores.attitudeScore() != null || scores.finalScore() != null;
    }
}
