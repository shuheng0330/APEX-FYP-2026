package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.AnnualAppraisalAccessRules;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Rechecks persisted permissions and current reporting, including for background work. */
@Component
@Transactional(readOnly = true, noRollbackFor={AccessDeniedException.class,BadRequestException.class})
public class EmployeeInsightAccess {
    private final StaffRepository staff;
    private final RoleAuthorityRepository permissions;
    private final ReviewPeriodParticipantRepository participants;
    private final AnnualAppraisalRepository appraisals;
    private final EmployeeInsightSettings settings;
    private final Environment environment;
    private final JdbcTemplate database;

    public EmployeeInsightAccess(StaffRepository staff, RoleAuthorityRepository permissions,
            ReviewPeriodParticipantRepository participants, AnnualAppraisalRepository appraisals,
            EmployeeInsightSettings settings, Environment environment, JdbcTemplate database) {
        this.staff = staff; this.permissions = permissions;
        this.participants = participants; this.appraisals = appraisals;
        this.settings = settings; this.environment = environment; this.database = database;
    }

    public void authoriseRequest(UUID actor) {
        subject(actor, AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL);
        requirePermission(actor, AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL);
    }

    public ReviewPeriodParticipant superior(Long participantId, UUID actor, boolean editable) {
        authoriseRequest(actor);
        return superiorScope(participantId, actor, editable);
    }

    public ReviewPeriodParticipant worker(Long participantId, UUID actor) {
        EmployeeInsightEvidenceService.requireEnvironment(settings, environment, database);
        requirePermission(actor, AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL);
        return superiorScope(participantId, actor, true);
    }

    public void submitted(AnnualAppraisal appraisal, UUID actor) {
        subject(actor, AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL, AuthorityName.CAN_REVIEW_ANNUAL_APPRAISAL);
        var account = account(actor);
        if (appraisal == null || appraisal.getParticipant() == null || appraisal.getStatus() == null
                || appraisal.getStatus() == AppraisalStatus.DRAFT) throw denied();
        var p = participant(appraisal.getParticipant().getId());
        if (Objects.equals(actor, p.getStaff().getId())
                || !Objects.equals(appraisal.getEmployeeId(), p.getStaff().getId())
                || !Objects.equals(appraisal.getReviewPeriodId(), p.getReviewPeriod().getId())) throw denied();
        if (tokenHas(AuthorityName.CAN_REVIEW_ANNUAL_APPRAISAL)
                && has(account, AuthorityName.CAN_REVIEW_ANNUAL_APPRAISAL)) return;
        if (tokenHas(AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL)
                && has(account, AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL)
                && currentSuperior(p, actor) && Objects.equals(appraisal.getSuperiorId(), actor)) return;
        throw denied();
    }

    private ReviewPeriodParticipant superiorScope(Long id, UUID actor, boolean editable) {
        var p = participant(id);
        if (!currentSuperior(p, actor) || !eligible(p)) throw denied();
        if (editable && !editable(p, actor))
            throw new BadRequestException("Employee insight is available only for an editable appraisal");
        return p;
    }

    public boolean editable(ReviewPeriodParticipant p, UUID actor) {
        if (!currentSuperior(p, actor) || !eligible(p) || p.getReviewPeriod() == null
                || (p.getReviewPeriod().getStatus() != AnnualKpiReviewPeriodStatus.OPEN
                    && p.getReviewPeriod().getStatus() != AnnualKpiReviewPeriodStatus.CLOSED)) return false;
        return appraisals.findByParticipantId(p.getId()).map(a -> Objects.equals(a.getSuperiorId(), actor)
                && (a.getStatus() == AppraisalStatus.DRAFT || a.getStatus() == AppraisalStatus.RETURNED)).orElse(true);
    }

    private ReviewPeriodParticipant participant(Long id) {
        if (id == null) throw denied();
        var p = participants.findById(id).orElseThrow(EmployeeInsightAccess::denied);
        if (p.getStaff() == null || p.getReviewPeriod() == null || p.getReviewPeriod().getStatus() == null
                || !Set.of(AnnualKpiReviewPeriodStatus.OPEN, AnnualKpiReviewPeriodStatus.CLOSED)
                        .contains(p.getReviewPeriod().getStatus())) throw denied();
        return p;
    }

    private void requirePermission(UUID actor, AuthorityName authority) {
        if (!has(account(actor), authority)) throw denied();
    }

    private Staff account(UUID actor) {
        if (actor == null) throw denied();
        var account = staff.findById(actor).orElseThrow(EmployeeInsightAccess::denied);
        if (account.isDeleted() || account.getAccountStatus() != StaffAccountStatus.ACTIVE
                || account.getRole() == null || account.getRole().isDeleted()) throw denied();
        return account;
    }

    private boolean has(Staff account, AuthorityName authority) {
        return account.getRole().getId() != null
                && permissions.existsByRoleIdAndAuthorityName(account.getRole().getId(), authority);
    }

    private void subject(UUID actor, AuthorityName... authorities) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (actor == null || authentication == null || !authentication.isAuthenticated()
                || !actor.toString().equals(authentication.getName())
                || Arrays.stream(authorities).noneMatch(EmployeeInsightAccess::tokenHas)) throw denied();
    }

    private static boolean tokenHas(AuthorityName authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.name().equals(a.getAuthority()));
    }

    public static boolean currentSuperior(ReviewPeriodParticipant p, UUID actor) {
        return AnnualAppraisalAccessRules.currentSuperior(p, actor);
    }

    public static boolean eligible(ReviewPeriodParticipant p) {
        return AnnualAppraisalAccessRules.eligible(p);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Employee insight is unavailable for this account");
    }
}
