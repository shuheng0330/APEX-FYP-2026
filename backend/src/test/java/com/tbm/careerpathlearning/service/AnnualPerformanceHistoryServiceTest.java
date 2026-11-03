package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import static com.tbm.careerpathlearning.dto.AnnualPerformanceHistoryDto.ComparisonStatus.*;
import static com.tbm.careerpathlearning.dto.AnnualPerformanceHistoryDto.SourceType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnualPerformanceHistoryServiceTest {
    ReviewPeriodParticipantRepository participants = mock(ReviewPeriodParticipantRepository.class);
    AnnualAppraisalRepository appraisals = mock(AnnualAppraisalRepository.class);
    AnnualAppraisalActivityRepository activity = mock(AnnualAppraisalActivityRepository.class);
    ReviewCheckpointRepository checkpoints = mock(ReviewCheckpointRepository.class);
    AnnualAppraisalReadinessService readiness = mock(AnnualAppraisalReadinessService.class);
    ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    AnnualPerformanceHistoryService service = new AnnualPerformanceHistoryService(
            participants, appraisals, activity, checkpoints, readiness, json);
    Staff employee = new Staff();
    ReviewPeriodParticipant anchor, historical;
    ReviewCheckpoint checkpoint;
    AnnualAppraisal appraisal;
    AnnualAppraisalSnapshotDto captured;
    List<AnnualAppraisalActivity> events = new ArrayList<>();

    @BeforeEach void setup() {
        employee.setId(UUID.randomUUID());
        anchor = participant(10, "2026 Selected Review", "2026-12-31", AnnualKpiReviewPeriodStatus.OPEN);
        historical = participant(9, "2025 Annual Review", "2025-12-31", AnnualKpiReviewPeriodStatus.CLOSED);
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employee.getId())).thenReturn(List.of(historical, anchor));
        checkpoint = new ReviewCheckpoint(); checkpoint.setId(20L); checkpoint.setSequenceNumber(1);
        checkpoint.setStartDate(historical.getReviewPeriod().getStartDate()); checkpoint.setEndDate(historical.getReviewPeriod().getEndDate());
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(9L, ReviewFrequency.ANNUALLY))
                .thenReturn(List.of(checkpoint));
        captured = snapshot("80");
        when(readiness.evaluate(historical)).thenReturn(ready(captured));
        appraisal = new AnnualAppraisal(); appraisal.setId(30L); appraisal.setParticipant(historical);
        appraisal.setEmployeeId(employee.getId()); appraisal.setReviewPeriodId(9L); appraisal.setStatus(AppraisalStatus.PENDING_REVIEW);
        appraisal.setSubmittedAt(OffsetDateTime.parse("2026-01-02T10:00:00Z")); appraisal.setSnapshot(captured);
        when(appraisals.findByParticipantId(historical.getId())).thenReturn(Optional.of(appraisal));
        when(activity.findAllByAppraisalIdOrderByIdAsc(30L)).thenAnswer(i -> events);
        event(1, "SUBMITTED", captured);
    }

    ReviewPeriodParticipant participant(long id, String name, String end, AnnualKpiReviewPeriodStatus status) {
        var p = new AnnualKpiReviewPeriod(); p.setId(id); p.setName(name); p.setEndDate(LocalDate.parse(end));
        p.setStartDate(LocalDate.of(p.getEndDate().getYear(), 1, 1)); p.setStatus(status);
        var result = new ReviewPeriodParticipant(); result.setId(id + 100); result.setReviewPeriod(p);
        result.setStaff(employee); result.setReviewFrequency(ReviewFrequency.ANNUALLY);
        return result;
    }

    AnnualAppraisalSnapshotDto snapshot(String value) {
        var score = new BigDecimal(value);
        return new AnnualAppraisalSnapshotDto(historical.getId(), 9L, employee.getId(), ReviewFrequency.ANNUALLY,
                AnnualKpiConsolidationMethod.FINAL_CHECKPOINT, new BigDecimal("50"), new BigDecimal("50"), score, score, score,
                List.of(new AnnualAppraisalSnapshotDto.Checkpoint(20L, 1, checkpoint.getStartDate(), checkpoint.getEndDate(),
                        40L, KpiAssessmentStatus.REVIEWED, score, OffsetDateTime.parse("2026-01-01T10:00:00Z"), List.of(), null)),
                new AnnualAppraisalSnapshotDto.Attitude(50L, 60L, AttitudeEvaluationFormat.OTHERS, AttitudeAssessmentStatus.REVIEWED,
                        OffsetDateTime.parse("2026-01-01T11:00:00Z"), List.of(new AnnualAppraisalSnapshotDto.Criterion(70L, "Respect", "Works respectfully", 4))));
    }

    AnnualAppraisalReadinessDto ready(AnnualAppraisalSnapshotDto snapshot) {
        return new AnnualAppraisalReadinessDto(true, List.of(), snapshot);
    }
    AnnualAppraisalActivity event(long id, String action, AnnualAppraisalSnapshotDto snapshot) {
        var e = new AnnualAppraisalActivity(); e.setId(id); e.setAppraisalId(30L); e.setAction(action); e.setSnapshot(snapshot);
        e.setOccurredAt(appraisal.getSubmittedAt().plusNanos(id)); events.add(e); return e;
    }
    AnnualPerformanceHistoryDto row() { return service.read(anchor).get(0); }

    @ParameterizedTest @EnumSource(value = AppraisalStatus.class, names = {"PENDING_REVIEW", "RETURNED", "APPROVED"})
    void submittedStatesUseRecordedScoresWithoutNeedingCurrentCompletion(AppraisalStatus status) {
        appraisal.setStatus(status);
        appraisal.setJustification("Changed after return; private text");
        event(2, "DRAFT_SAVED", null);
        when(readiness.evaluate(historical)).thenReturn(new AnnualAppraisalReadinessDto(false, List.of("Missing results"), null));
        var row = row();
        assertEquals(SUBMISSION_SNAPSHOT, row.sourceType()); assertEquals(CURRENT_RESULTS_UNAVAILABLE, row.comparisonStatus());
        assertEquals(new BigDecimal("80"), row.finalScore());
        assertEquals("2025 Annual Review", row.periodName()); assertEquals(LocalDate.of(2025, 1, 1), row.startDate());
        assertEquals(LocalDate.of(2025, 12, 31), row.endDate());
        assertFalse(json.valueToTree(row).has("justification")); assertFalse(json.valueToTree(row).has("finalDecision"));
    }

    @Test void aNeverSubmittedDraftIsNeverARecordedSnapshotEvenWithSnapshotLikeContent() {
        appraisal.setStatus(AppraisalStatus.DRAFT); appraisal.setSubmittedAt(null);
        when(readiness.evaluate(historical)).thenReturn(ready(snapshot("90")));
        assertEquals(CALCULATED_RESULTS, row().sourceType()); assertEquals(NOT_APPLICABLE, row().comparisonStatus());
        assertEquals(new BigDecimal("90"), row().finalScore()); verifyNoInteractions(activity);
    }

    @Test void completedResultsWithoutAnyAppraisalAreIncludedAndIncompleteResultsAreExcluded() {
        when(appraisals.findByParticipantId(historical.getId())).thenReturn(Optional.empty());
        assertEquals(CALCULATED_RESULTS, row().sourceType());
        when(readiness.evaluate(historical)).thenReturn(new AnnualAppraisalReadinessDto(false, List.of("Missing Company results"), captured));
        assertTrue(service.read(anchor).isEmpty()); verifyNoInteractions(activity);
    }

    @Test void repeatedResubmissionsUseOnlyTheLatestSubmissionAndKeepEarlierEvents() {
        var old = captured; event(2, "RETURNED", old);
        var second = snapshot("85"); event(3, "RESUBMITTED", second); event(4, "RETURNED", second);
        var latest = snapshot("90"); event(5, "RESUBMITTED", latest); event(6, "APPROVED", latest);
        appraisal.setSnapshot(latest); appraisal.setStatus(AppraisalStatus.APPROVED);
        assertEquals(new BigDecimal("90"), row().finalScore()); assertEquals(DIFFERENT, row().comparisonStatus());
        assertSame(old, events.get(0).getSnapshot()); assertSame(second, events.get(2).getSnapshot());
        assertSame(latest, appraisal.getSnapshot()); assertEquals(6, events.size());
        events.get(4).setSnapshot(null);
        assertEquals(CALCULATED_RESULTS, row().sourceType()); // Do not fall back to the older valid activity.
    }

    @Test void provenanceAndCurrentScoreComparisonIgnoreNumericScaleButNotActualChanges() {
        events.get(0).setSnapshot(snapshot("80.0000"));
        var current = snapshot("80.00");
        current = mutate(current, "attitude",
                new AnnualAppraisalSnapshotDto.Attitude(50L, 60L, AttitudeEvaluationFormat.OTHERS, AttitudeAssessmentStatus.REVIEWED,
                        OffsetDateTime.now(), current.attitude().criteria()));
        when(readiness.evaluate(historical)).thenReturn(ready(current));
        assertEquals(SUBMISSION_SNAPSHOT, row().sourceType()); assertEquals(UNCHANGED, row().comparisonStatus());
        when(readiness.evaluate(historical)).thenReturn(ready(snapshot("80.0001")));
        assertEquals(DIFFERENT, row().comparisonStatus()); assertEquals(new BigDecimal("80"), row().finalScore());
        assertSame(captured, appraisal.getSnapshot()); assertEquals(new BigDecimal("80.0000"), events.get(0).getSnapshot().finalScore());
    }

    @ParameterizedTest @ValueSource(strings = {"annualKpiScore", "attitudeScore", "finalScore"})
    void eachScoreCanIndependentlyIndicateLaterCorrections(String field) {
        var changed = mutate(captured, field, new BigDecimal("81"));
        when(readiness.evaluate(historical)).thenReturn(ready(changed));
        assertEquals(DIFFERENT, row().comparisonStatus()); assertEquals(new BigDecimal("80"), row().finalScore());
    }

    @Test void validZeroesAndZeroCompositionWeightsAreRetained() {
        var zero = mutate(mutate(snapshot("0"), "kpiWeight", BigDecimal.ZERO), "attitudeWeight", new BigDecimal("100"));
        appraisal.setSnapshot(zero); events.get(0).setSnapshot(zero); when(readiness.evaluate(historical)).thenReturn(ready(zero));
        assertEquals(SUBMISSION_SNAPSHOT, row().sourceType()); assertEquals(0, row().finalScore().signum());
        assertEquals(UNCHANGED, row().comparisonStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"missingSubmission", "missingSnapshot", "missingSubmittedAt", "nullStatus",
            "wrongActivityAppraisal", "wrongAppraisalEmployee", "wrongAppraisalPeriod", "wrongAppraisalParticipant",
            "mismatchedLatestSubmission", "draftWithSubmittedAt"})
    void invalidSubmissionProvenanceFallsBackWithoutRepair(String invalid) {
        switch (invalid) {
            case "missingSubmission" -> { events.clear(); event(2, "APPROVED", captured); }
            case "missingSnapshot" -> appraisal.setSnapshot(null);
            case "missingSubmittedAt" -> appraisal.setSubmittedAt(null);
            case "nullStatus" -> appraisal.setStatus(null);
            case "wrongActivityAppraisal" -> events.get(0).setAppraisalId(999L);
            case "wrongAppraisalEmployee" -> appraisal.setEmployeeId(UUID.randomUUID());
            case "wrongAppraisalPeriod" -> appraisal.setReviewPeriodId(999L);
            case "wrongAppraisalParticipant" -> appraisal.setParticipant(anchor);
            case "mismatchedLatestSubmission" -> event(2, "RESUBMITTED", snapshot("90"));
            case "draftWithSubmittedAt" -> appraisal.setStatus(AppraisalStatus.DRAFT);
        }
        var stored = appraisal.getSnapshot();
        assertEquals(CALCULATED_RESULTS, row().sourceType()); assertSame(stored, appraisal.getSnapshot());
        when(readiness.evaluate(historical)).thenReturn(new AnnualAppraisalReadinessDto(false, List.of("Incomplete"), null));
        assertTrue(service.read(anchor).isEmpty());
    }

    // Mutate a serialised copy so every malformed case starts with an otherwise realistic captured result.
    AnnualAppraisalSnapshotDto mutate(AnnualAppraisalSnapshotDto source, String field, Object value) {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(source);
        node.set(field, json.valueToTree(value));
        try { return json.treeToValue(node, AnnualAppraisalSnapshotDto.class); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    static Stream<Arguments> malformedSnapshots() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("annualKpiScore", "attitudeScore", "finalScore", "kpiWeight", "attitudeWeight")) {
            cases.add(Arguments.of(field + " missing", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, field, null)));
            cases.add(Arguments.of(field + " negative", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, field, new BigDecimal("-0.0001"))));
            cases.add(Arguments.of(field + " excessive", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, field, new BigDecimal("100.0001"))));
        }
        for (var field : List.of("participantId", "reviewPeriodId", "employeeId", "reviewFrequency", "consolidationMethod", "attitude", "checkpoints"))
            cases.add(Arguments.of(field + " missing", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, field, null)));
        cases.add(Arguments.of("wrong employee", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, "employeeId", UUID.randomUUID())));
        cases.add(Arguments.of("wrong participant", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, "participantId", 999L)));
        cases.add(Arguments.of("wrong period", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, "reviewPeriodId", 999L)));
        cases.add(Arguments.of("wrong frequency", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, "reviewFrequency", ReviewFrequency.MONTHLY)));
        cases.add(Arguments.of("weights not 100", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, "kpiWeight", new BigDecimal("51"))));
        cases.add(Arguments.of("missing checkpoint", (UnaryOperator<AnnualAppraisalSnapshotDto>) s -> replace(s, "checkpoints", List.of())));
        return cases.stream();
    }
    static AnnualAppraisalSnapshotDto replace(AnnualAppraisalSnapshotDto s, String field, Object value) {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(s); node.set(field, mapper.valueToTree(value));
        try { return mapper.treeToValue(node, AnnualAppraisalSnapshotDto.class); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    @ParameterizedTest(name = "{0}") @MethodSource("malformedSnapshots")
    void malformedSnapshotsFallBackToCalculatedResults(String label, UnaryOperator<AnnualAppraisalSnapshotDto> corrupt) {
        appraisal.setSnapshot(corrupt.apply(captured)); events.get(0).setSnapshot(appraisal.getSnapshot());
        assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @ParameterizedTest @ValueSource(strings = {"checkpointId", "assessmentId", "status", "score", "startDate", "endDate", "sequenceNumber"})
    void incompleteOrWrongCapturedCheckpointFallsBack(String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(captured.checkpoints().get(0));
        if (field.equals("sequenceNumber")) node.put(field, 99); else node.putNull(field);
        appraisal.setSnapshot(mutate(captured, "checkpoints", List.of(json.treeToValue(node, AnnualAppraisalSnapshotDto.Checkpoint.class))));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @ParameterizedTest @ValueSource(strings = {"-0.0001", "100.0001"})
    void outOfRangeCapturedCheckpointScoresFallBack(String score) {
        var c = captured.checkpoints().get(0);
        var invalid = new AnnualAppraisalSnapshotDto.Checkpoint(c.checkpointId(), c.sequenceNumber(), c.startDate(), c.endDate(),
                c.assessmentId(), c.status(), new BigDecimal(score), c.reviewedAt(), c.versions(), c.breakdown());
        appraisal.setSnapshot(mutate(captured, "checkpoints", List.of(invalid)));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @Test void duplicatedCheckpointsOrAssessmentsCannotPretendToCoverTheSchedule() {
        var second = new ReviewCheckpoint(); second.setId(21L); second.setSequenceNumber(2);
        second.setStartDate(checkpoint.getStartDate()); second.setEndDate(checkpoint.getEndDate());
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(9L, ReviewFrequency.ANNUALLY))
                .thenReturn(List.of(checkpoint, second));
        var c = captured.checkpoints().get(0);
        appraisal.setSnapshot(mutate(captured, "checkpoints", List.of(c, c)));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
        var duplicateAssessment = new AnnualAppraisalSnapshotDto.Checkpoint(21L, 2, c.startDate(), c.endDate(), c.assessmentId(),
                c.status(), c.score(), c.reviewedAt(), c.versions(), c.breakdown());
        appraisal.setSnapshot(mutate(captured, "checkpoints", List.of(c, duplicateAssessment)));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @ParameterizedTest @ValueSource(strings = {"assessmentId", "configurationId", "format", "status", "criteria"})
    void incompleteCapturedAttitudeFallsBack(String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(captured.attitude()); node.putNull(field);
        appraisal.setSnapshot(mutate(captured, "attitude", json.treeToValue(node, AnnualAppraisalSnapshotDto.Attitude.class)));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 6})
    void invalidSuperiorRatingsFallBack(int point) {
        var a = captured.attitude();
        appraisal.setSnapshot(mutate(captured, "attitude", new AnnualAppraisalSnapshotDto.Attitude(a.assessmentId(), a.configurationId(),
                a.format(), a.status(), a.reviewedAt(), List.of(new AnnualAppraisalSnapshotDto.Criterion(70L, "Respect", "", point)))));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "duplicate", "missingPoint", "missingIdentity"})
    void missingOrDuplicatedCapturedCriteriaFallBack(String invalid) {
        var a = captured.attitude(); var criterion = a.criteria().get(0);
        var criteria = switch (invalid) {
            case "empty" -> List.<AnnualAppraisalSnapshotDto.Criterion>of();
            case "duplicate" -> List.of(criterion, criterion);
            case "missingPoint" -> List.of(new AnnualAppraisalSnapshotDto.Criterion(70L, "Respect", "", null));
            default -> List.of(new AnnualAppraisalSnapshotDto.Criterion(null, "Respect", "", 4));
        };
        appraisal.setSnapshot(mutate(captured, "attitude", new AnnualAppraisalSnapshotDto.Attitude(a.assessmentId(), a.configurationId(),
                a.format(), a.status(), a.reviewedAt(), criteria)));
        events.get(0).setSnapshot(appraisal.getSnapshot()); assertEquals(CALCULATED_RESULTS, row().sourceType());
    }

    @Test void periodBoundariesStatusesAndSameYearPeriodsAreDistinctAndDeterministic() {
        var earlier = participant(8, "2025 Jan-Jun", "2025-06-30", AnnualKpiReviewPeriodStatus.OPEN);
        var sameDate = participant(11, "2025 Second Period", "2025-12-31", AnnualKpiReviewPeriodStatus.CLOSED);
        var first = participant(5, "2022 Boundary", "2022-01-01", AnnualKpiReviewPeriodStatus.CLOSED);
        var outside = participant(4, "2021 Outside", "2021-12-31", AnnualKpiReviewPeriodStatus.CLOSED);
        var future = participant(12, "2027 Future", "2027-01-01", AnnualKpiReviewPeriodStatus.OPEN);
        var draft = participant(13, "Draft", "2024-12-31", AnnualKpiReviewPeriodStatus.DRAFT);
        var upcoming = participant(14, "Upcoming", "2024-12-31", AnnualKpiReviewPeriodStatus.UPCOMING);
        var foreign = participant(15, "Another employee", "2024-12-31", AnnualKpiReviewPeriodStatus.CLOSED);
        foreign.setStaff(new Staff()); foreign.getStaff().setId(UUID.randomUUID());
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employee.getId()))
                .thenReturn(List.of(anchor, future, sameDate, draft, historical, outside, upcoming, earlier, foreign, first));
        for (var p : List.of(earlier, sameDate, first)) when(readiness.evaluate(p)).thenReturn(ready(captured));
        var rows = service.read(anchor);
        assertEquals(List.of(5L, 8L, 9L, 11L), rows.stream().map(AnnualPerformanceHistoryDto::reviewPeriodId).toList());
        assertEquals(List.of("2022 Boundary", "2025 Jan-Jun", "2025 Annual Review", "2025 Second Period"),
                rows.stream().map(AnnualPerformanceHistoryDto::periodName).toList());
        for (var p : List.of(anchor, outside, future, draft, upcoming, foreign)) verify(readiness, never()).evaluate(p);
    }

    @Test void aPeriodEndingOnTheAnchorDateIsIncludedButLaterInTheSameYearIsExcluded() {
        anchor.getReviewPeriod().setEndDate(LocalDate.of(2026, 6, 30));
        historical.getReviewPeriod().setEndDate(LocalDate.of(2026, 6, 30));
        assertEquals(1, service.read(anchor).size());
        historical.getReviewPeriod().setEndDate(LocalDate.of(2026, 7, 1));
        assertTrue(service.read(anchor).isEmpty());
    }

    @AfterEach void noWrites() {
        // Only these read methods are permitted; saves, locks, deletes and flushes fail this assertion.
        for (var call : mockingDetails(participants).getInvocations()) assertEquals("findAllByStaffIdOrderByReviewPeriodStartDateDesc", call.getMethod().getName());
        for (var call : mockingDetails(appraisals).getInvocations()) assertEquals("findByParticipantId", call.getMethod().getName());
        for (var call : mockingDetails(activity).getInvocations()) assertEquals("findAllByAppraisalIdOrderByIdAsc", call.getMethod().getName());
        for (var call : mockingDetails(checkpoints).getInvocations()) assertEquals("findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc", call.getMethod().getName());
    }
}
