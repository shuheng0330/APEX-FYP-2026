package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessStaffDto;
import com.tbm.careerpathlearning.dto.TrainerOwnedCourseDto;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainerAccessGrant;
import com.tbm.careerpathlearning.repository.AuthorityRepository;
import com.tbm.careerpathlearning.repository.RoleAuthorityRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainerAccessGrantRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainerAccessServiceImpl implements TrainerAccessService {

    static final int MAX_REASON_LENGTH = 500;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Autowired private TrainerAccessGrantRepository trainerAccessGrantRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private AuthorityRepository authorityRepository;
    @Autowired private RoleAuthorityRepository roleAuthorityRepository;
    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private TrainingAuditService trainingAuditService;
    @Autowired private NotificationService notificationService;
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveGrant(UUID staffId) {
        LocalDate today = LocalDate.now(clock);
        return trainerAccessGrantRepository.findById(staffId).map(g -> g.activeOn(today)).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerAccessStaffDto> list() {
        List<Staff> staff = staffRepository.findAllActiveWithRole();
        Set<Long> trainerRoles = rolesWith(AuthorityName.CAN_MANAGE_TRAINING, staff);
        Map<UUID, TrainerAccessGrant> grants = trainerAccessGrantRepository.findAll().stream()
                .collect(Collectors.toMap(TrainerAccessGrant::getStaffId, Function.identity()));
        Map<UUID, Long> owned = sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(d -> d.getUploadedBy() != null)
                .collect(Collectors.groupingBy(SopDocument::getUploadedBy, Collectors.counting()));
        return staff.stream()
                .sorted(Comparator.comparing(Staff::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> toDto(s, trainerRoles, grants.get(s.getId()), owned.getOrDefault(s.getId(), 0L).intValue()))
                .toList();
    }

    @Override
    @Transactional
    public TrainerAccessStaffDto grant(UUID staffId, TrainerAccessRequest request, UUID adminId) {
        Staff staff = staffRepository.findByIdAndIsDeletedFalse(staffId)
                .filter(s -> s.getAccountStatus() == StaffAccountStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Trainer access can only be given to an active staff member."));
        String reason = reason(request);
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = request.getEndDate();
        if (endDate != null && endDate.isBefore(today)) {
            throw new BadRequestException("The end date cannot be in the past.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        TrainerAccessGrant grant = trainerAccessGrantRepository.findById(staffId).orElse(null);

        if (grant != null && grant.activeOn(today)) {
            if (Objects.equals(grant.getEndDate(), endDate)) {
                throw new BadRequestException("Nothing changed: " + staff.getName() + "'s Trainer access already has that end date.");
            }
            String detail = "Last day changed from " + describe(grant.getEndDate()) + " to " + describe(endDate);
            grant.setEndDate(endDate);
            grant.setUpdatedAt(now);
            trainerAccessGrantRepository.save(grant);
            trainingAuditService.record(adminId, TrainingAuditAction.TRAINER_ACCESS_END_DATE_CHANGED,
                    TrainingAuditTarget.STAFF, staffId.toString(), reason, detail);
            return toDto(staff);
        }

        if (grant == null) {
            grant = new TrainerAccessGrant();
            grant.setStaffId(staffId);
        } else if (grant.getRevokedAt() == null && grant.getLapsedAt() == null) {
            // The end date passed before the daily job recorded it: record the lapse first so the history stays complete.
            trainingAuditService.record(null, TrainingAuditAction.TRAINER_ACCESS_LAPSED, TrainingAuditTarget.STAFF,
                    staffId.toString(), null, "Ended after " + DATE.format(grant.getEndDate()));
        }
        grant.setEndDate(endDate);
        grant.setGrantedBy(adminId);
        grant.setGrantedAt(now);
        grant.setRevokedAt(null);
        grant.setLapsedAt(null);
        grant.setUpdatedAt(now);
        trainerAccessGrantRepository.save(grant);
        trainingAuditService.record(adminId, TrainingAuditAction.TRAINER_ACCESS_GRANTED, TrainingAuditTarget.STAFF,
                staffId.toString(), reason, endDate == null ? "No end date" : "Last day: " + DATE.format(endDate));
        return toDto(staff);
    }

    @Override
    @Transactional
    public TrainerAccessStaffDto revoke(UUID staffId, TrainerAccessRequest request, UUID adminId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BadRequestException("Staff member not found."));
        String reason = reason(request);
        boolean viaRole = !rolesWith(AuthorityName.CAN_MANAGE_TRAINING, List.of(staff)).isEmpty();
        TrainerAccessGrant grant = trainerAccessGrantRepository.findById(staffId).orElse(null);
        if (grant == null || !grant.activeOn(LocalDate.now(clock))) {
            throw new BadRequestException(staff.getName() + " has no individual Trainer access to revoke."
                    + (viaRole ? " Their access comes from their job role; change it on the Roles page." : ""));
        }
        if (!viaRole) {
            List<String> courses = owned(staffId).stream().map(SopDocument::getTitle).toList();
            if (!courses.isEmpty()) {
                throw new BadRequestException("Transfer their courses first",
                        staff.getName() + " owns " + courses.size() + (courses.size() == 1 ? " course: " : " courses: ")
                                + String.join(", ", courses) + ". Transfer them to another trainer before revoking.");
            }
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        grant.setRevokedAt(now);
        grant.setUpdatedAt(now);
        trainerAccessGrantRepository.save(grant);
        trainingAuditService.record(adminId, TrainingAuditAction.TRAINER_ACCESS_REVOKED, TrainingAuditTarget.STAFF,
                staffId.toString(), reason, null);
        return toDto(staff);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingAuditEntryDto> history(UUID staffId) {
        return trainingAuditService.history(TrainingAuditTarget.STAFF, staffId.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerOwnedCourseDto> ownedCourses(UUID staffId) {
        return owned(staffId).stream().map(d -> {
            TrainerOwnedCourseDto dto = new TrainerOwnedCourseDto();
            dto.setId(d.getId());
            dto.setTitle(d.getTitle());
            dto.setVersion(d.getVersion());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public int lapseExpiredGrants() {
        List<TrainerAccessGrant> expired = trainerAccessGrantRepository
                .findAllByRevokedAtIsNullAndLapsedAtIsNullAndEndDateBefore(LocalDate.now(clock));
        if (expired.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Staff> active = staffRepository.findAllActiveWithRole();
        Set<Long> adminRoles = rolesWith(AuthorityName.CAN_MANAGE_ACCESS_CONTROL, active);
        List<Staff> admins = active.stream().filter(s -> s.getRole() != null && adminRoles.contains(s.getRole().getId())).toList();

        for (TrainerAccessGrant grant : expired) {
            grant.setLapsedAt(now);
            grant.setUpdatedAt(now);
            trainerAccessGrantRepository.save(grant);
            String lastDay = DATE.format(grant.getEndDate());
            trainingAuditService.record(null, TrainingAuditAction.TRAINER_ACCESS_LAPSED, TrainingAuditTarget.STAFF,
                    grant.getStaffId().toString(), null, "Ended after " + lastDay);

            Staff person = staffRepository.findById(grant.getStaffId()).orElse(null);
            boolean keepsAccess = person != null && !rolesWith(AuthorityName.CAN_MANAGE_TRAINING, List.of(person)).isEmpty();
            List<String> courses = owned(grant.getStaffId()).stream().map(SopDocument::getTitle).toList();
            if (keepsAccess || courses.isEmpty()) {
                continue;
            }
            String name = person == null ? "A former staff member" : person.getName();
            for (Staff admin : admins) {
                notificationService.notifyOnce("trainer-access-lapsed:" + grant.getStaffId() + ":" + grant.getEndDate() + ":" + admin.getId(),
                        admin.getId(), NotificationType.TRAINER_ACCESS_LAPSED, "Trainer access ended: " + name,
                        name + "'s Trainer access ended after " + lastDay + ", but they still own " + courses.size()
                                + (courses.size() == 1 ? " course: " : " courses: ") + String.join(", ", courses)
                                + ". Transfer them to another trainer.",
                        "/sop/trainer-access");
            }
        }
        return expired.size();
    }

    // === helpers ============================================================

    private List<SopDocument> owned(UUID staffId) {
        return sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(d -> staffId.equals(d.getUploadedBy()))
                .toList();
    }

    /** The job roles, among the given staff's, whose permissions include the given one. */
    private Set<Long> rolesWith(AuthorityName permission, Collection<Staff> staff) {
        Long authorityId = authorityRepository.findByName(permission).map(Authority::getId).orElse(null);
        Set<Long> roleIds = staff.stream().map(Staff::getRole).filter(Objects::nonNull).map(Role::getId)
                .collect(Collectors.toSet());
        if (authorityId == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleAuthorityRepository.findAllByRoleIdIn(roleIds).stream()
                .filter(ra -> authorityId.equals(ra.getId().getAuthorityId()))
                .map(ra -> ra.getId().getRoleId())
                .collect(Collectors.toSet());
    }

    private TrainerAccessStaffDto toDto(Staff staff) {
        return toDto(staff, rolesWith(AuthorityName.CAN_MANAGE_TRAINING, List.of(staff)), trainerAccessGrantRepository.findById(staff.getId()).orElse(null),
                owned(staff.getId()).size());
    }

    private TrainerAccessStaffDto toDto(Staff s, Set<Long> trainerRoles, TrainerAccessGrant grant, int ownedCourses) {
        TrainerAccessStaffDto dto = new TrainerAccessStaffDto();
        dto.setStaffId(s.getId());
        dto.setName(s.getName());
        dto.setEmail(s.getEmail());
        OrgChart department = SopAssignmentServiceImpl.department(s);
        dto.setDepartment(department == null ? null : department.getName());
        dto.setJobRole(s.getRole() == null ? null : s.getRole().getName());
        dto.setViaRole(s.getRole() != null && trainerRoles.contains(s.getRole().getId()));
        if (grant != null && grant.activeOn(LocalDate.now(clock))) {
            dto.setViaGrant(true);
            dto.setGrantEndDate(grant.getEndDate());
            dto.setGrantedAt(grant.getGrantedAt());
            dto.setGrantedByName(staffRepository.findById(grant.getGrantedBy()).map(Staff::getName).orElse("Unknown user"));
        }
        dto.setOwnedCourses(ownedCourses);
        return dto;
    }

    private static String reason(TrainerAccessRequest request) {
        String reason = request == null || request.getReason() == null ? "" : request.getReason().strip();
        if (reason.isEmpty()) {
            throw new BadRequestException("Give a reason for this change.");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Keep the reason under " + MAX_REASON_LENGTH + " characters.");
        }
        return reason;
    }

    private static String describe(LocalDate endDate) {
        return endDate == null ? "no end date" : DATE.format(endDate);
    }
}
