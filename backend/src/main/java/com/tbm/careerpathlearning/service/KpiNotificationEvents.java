package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Synchronous events published only by successful write transitions, inside their transaction. */
public final class KpiNotificationEvents {
    private KpiNotificationEvents() {}

    // Original KPI plans have no append-only transition ID; each successful transition gets an event ID.
    public record Plan(KpiPlan plan, String action, UUID actor, UUID eventId) {
        public Plan(KpiPlan plan, String action, UUID actor) { this(plan, action, actor, UUID.randomUUID()); }
    }
    public record Revision(KpiPlanVersion version, UUID actor) {}
    public record Assistance(IndividualKpiAssistanceAuthorization request, UUID actor) {}
    public record KpiReview(KpiAssessment assessment, UUID actor) {}
    public record AttitudeReview(AttitudeAssessment assessment, UUID actor) {}
    public record Appraisal(AnnualAppraisal appraisal, AnnualAppraisalActivity activity, UUID actor) {}
    public record Enrolled(List<ReviewPeriodParticipant> participants, UUID actor) {
        public Enrolled { participants = List.copyOf(participants); }
    }
    public record Score(KpiAssessment assessment, BigDecimal previousScore, int calculation,
                        UUID actor, boolean reviewCompletion) {}
}
