package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.StaffRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class StaffPerformanceAccess {
    public static final String TEAM = "CAN_VIEW_TEAM_PERFORMANCE";
    public static final String DEPARTMENT = "CAN_VIEW_DEPARTMENT_PERFORMANCE";
    public static final String ORGANISATION = "CAN_VIEW_ORGANISATION_PERFORMANCE";
    private final StaffRepository staff;
    private final PerformanceDepartmentResolver departments;

    public StaffPerformanceAccess(StaffRepository staff, PerformanceDepartmentResolver departments) {
        this.staff = staff;
        this.departments = departments;
    }

    public Staff actor(UUID actorId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !actorId.toString().equals(auth.getName())) throw denied();
        var actor = staff.findById(actorId).orElseThrow(StaffPerformanceAccess::denied);
        if (actor.isDeleted() || actor.getAccountStatus() != StaffAccountStatus.ACTIVE) throw denied();
        return actor;
    }

    public void employee(Staff employee, Staff actor) {
        if (employee.getId().equals(actor.getId())) {
            if (!has("ROLE_USER")) throw denied();
            return;
        }
        if (has(ORGANISATION) || directReport(employee, actor)) return;
        // Department access is checked per recorded participant, before returning any period.
        if (!has(DEPARTMENT)) throw denied();
    }

    public boolean participant(ReviewPeriodParticipant participant, Staff actor) {
        var employee = participant.getStaff();
        if (employee.getId().equals(actor.getId())) return has("ROLE_USER");
        if (has(ORGANISATION) || directReport(employee, actor)) return true;
        if (!has(DEPARTMENT)) return false;
        var department = departments.resolve(actor.getRole());
        return department != null && participant.getDepartment() != null
                && Objects.equals(department.getId(), participant.getDepartment().getId());
    }

    public boolean internalAppraisal(ReviewPeriodParticipant participant, Staff actor) {
        if (participant.getStaff().getId().equals(actor.getId())) return false;
        if (has("CAN_REVIEW_ANNUAL_APPRAISAL")) return true;
        var manager = participant.getStaff().getManager();
        return has("CAN_SUBMIT_ANNUAL_APPRAISAL") && manager != null
                && Objects.equals(manager.getId(), actor.getId());
    }

    public boolean has(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }

    private boolean directReport(Staff employee, Staff actor) {
        return has(TEAM) && !employee.isDeleted() && employee.getManager() != null
                && Objects.equals(employee.getManager().getId(), actor.getId());
    }

    public static AccessDeniedException denied() {
        return new AccessDeniedException("This performance information is unavailable in your authorised scope");
    }
}
