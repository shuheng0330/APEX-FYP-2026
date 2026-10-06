package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.model.*;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ReviewPeriodParticipantFactory {
    public ReviewPeriodParticipant snapshot(AnnualKpiReviewPeriod period, Staff staff,
                                            ReviewPeriodRoleConfiguration configuration, OrgChart department) {
        Objects.requireNonNull(period, "Review period is required");
        Objects.requireNonNull(staff, "Staff is required");
        Objects.requireNonNull(configuration, "Period role configuration is required");
        if (!samePeriod(period, configuration.getReviewPeriod())
                || !sameRole(staff.getRole(), configuration.getRole()) || configuration.getReviewFrequency() == null) {
            throw new IllegalArgumentException("Configuration must belong to this period and the employee's role");
        }
        if (department != null && department.getType() != OrgChartType.D) {
            throw new IllegalArgumentException("Department must be a department organisation node");
        }
        ReviewPeriodParticipant participant = new ReviewPeriodParticipant();
        participant.setReviewPeriod(period);
        participant.setStaff(staff);
        participant.setStaffName(staff.getName());
        participant.setRole(configuration.getRole());
        participant.setRoleName(configuration.getRole().getName());
        participant.setDepartment(department);
        participant.setDepartmentName(department == null ? null : department.getName());
        participant.setSuperior(staff.getManager());
        participant.setSuperiorName(staff.getManager() == null ? null : staff.getManager().getName());
        participant.setReviewFrequency(configuration.getReviewFrequency());
        return participant;
    }

    private boolean samePeriod(AnnualKpiReviewPeriod first, AnnualKpiReviewPeriod second) {
        return second != null && (first == second || first.getId() != null && first.getId().equals(second.getId()));
    }

    private boolean sameRole(Role first, Role second) {
        return first != null && second != null
                && (first == second || first.getId() != null && first.getId().equals(second.getId()));
    }
}
