package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.ReviewPeriodParticipant;
import java.util.UUID;

/** Shared, side-effect-free appraisal eligibility and live reporting predicates. */
public final class AnnualAppraisalAccessRules {
    private AnnualAppraisalAccessRules() {}

    public static boolean currentSuperior(ReviewPeriodParticipant p, UUID actor) {
        if (p == null || p.getStaff() == null || actor == null) return false;
        var manager = p.getStaff().getManager();
        return manager != null && actor.equals(manager.getId()) && !actor.equals(p.getStaff().getId())
                && !manager.isDeleted() && manager.getAccountStatus() == StaffAccountStatus.ACTIVE;
    }

    public static boolean eligible(ReviewPeriodParticipant p) {
        if (p == null || p.getStaff() == null) return false;
        var employee = p.getStaff();
        return !employee.isDeleted() && employee.getAccountStatus() == StaffAccountStatus.ACTIVE
                && employee.getRole() != null && !employee.getRole().isDeleted()
                && employee.getRole().isPerformanceReviewEligible();
    }
}
