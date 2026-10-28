package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.NotificationListDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.RoleAuthority;
import com.tbm.careerpathlearning.model.SopAssignment;
import com.tbm.careerpathlearning.model.SopCourseFeedback;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopMaterialView;
import com.tbm.careerpathlearning.model.SopMaterialReviewEvent;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizAttempt;
import com.tbm.careerpathlearning.model.SopQuizDraw;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.SopQuizReviewEvent;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainerAccessGrant;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.AuthorityRepository;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.repository.RoleAuthorityRepository;
import com.tbm.careerpathlearning.repository.RoleRepository;
import com.tbm.careerpathlearning.repository.SopAssignmentRepository;
import com.tbm.careerpathlearning.repository.SopCourseFeedbackRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.SopMaterialViewRepository;
import com.tbm.careerpathlearning.repository.SopMaterialReviewEventRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizAttemptRepository;
import com.tbm.careerpathlearning.repository.SopQuizDrawRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import com.tbm.careerpathlearning.repository.SopQuizReviewEventRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainerAccessGrantRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingAuditLogRepository;
import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import com.tbm.careerpathlearning.service.SopGenerationQueue;
import com.tbm.careerpathlearning.service.SopVersionService;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory stand-ins for the training repositories, so the training services can be tested through whole
 * flows (assign, start, fail, retake, complete) without a database. Only the repository methods the
 * services call are implemented; any other call fails the test with UnsupportedOperationException.
 */
class SopTestStore {

    /** 12 October 2026, 10:00 in Malaysia. */
    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");

    private static final Set<Class<?>> REPOSITORIES = Set.of(SopDocumentRepository.class, SopModuleRepository.class,
            SopQuizQuestionRepository.class, SopQuizReviewEventRepository.class, SopMaterialReviewEventRepository.class,
            SopAssignmentRepository.class, SopQuizDrawRepository.class, SopMaterialViewRepository.class,
            SopEnrollmentRepository.class, SopQuizAttemptRepository.class, SopCourseFeedbackRepository.class,
            StaffRepository.class, TrainerAccessGrantRepository.class, TrainingAuditLogRepository.class,
            AuthorityRepository.class, RoleAuthorityRepository.class, OrgChartRepository.class, RoleRepository.class,
            TrainingProgramRepository.class, TrainingRegistrationRepository.class, TrainingInvitationRepository.class,
            TrainingAttendanceRepository.class);

    final Map<Long, SopDocument> docs = new LinkedHashMap<>();
    final Map<Long, SopModule> modules = new LinkedHashMap<>();
    final Map<Long, SopQuizQuestion> questions = new LinkedHashMap<>();
    final Map<Long, SopQuizReviewEvent> events = new LinkedHashMap<>();
    final Map<Long, SopMaterialReviewEvent> materialEvents = new LinkedHashMap<>();
    final Map<Long, SopAssignment> assignments = new LinkedHashMap<>();
    final Map<Long, SopEnrollment> enrollments = new LinkedHashMap<>();
    final Map<Long, SopQuizAttempt> attempts = new LinkedHashMap<>();
    final Map<Long, SopCourseFeedback> feedback = new LinkedHashMap<>();
    final Map<Long, SopQuizDraw> draws = new LinkedHashMap<>();
    final Map<Long, SopMaterialView> views = new LinkedHashMap<>();
    final Map<UUID, Staff> staff = new LinkedHashMap<>();
    final Map<UUID, TrainerAccessGrant> grants = new LinkedHashMap<>();
    final Map<Long, TrainingAuditLog> auditLog = new LinkedHashMap<>();
    final Map<AuthorityName, Authority> authorities = new EnumMap<>(AuthorityName.class);
    final Map<Long, OrgChart> orgCharts = new LinkedHashMap<>();
    final Map<Long, Role> roles = new LinkedHashMap<>();
    /** Training sessions (FR-12), with their registrations, invitations and attendance. */
    final Map<Long, TrainingProgram> sessions = new LinkedHashMap<>();
    final Map<Long, TrainingRegistration> registrations = new LinkedHashMap<>();
    final Map<Long, TrainingInvitation> invitations = new LinkedHashMap<>();
    final Map<Long, TrainingAttendance> attendance = new LinkedHashMap<>();
    final List<RoleAuthority> roleAuthorities = new ArrayList<>();
    /** Notifications the services sent, in order. */
    final List<Sent> notifications = new ArrayList<>();
    final TestClock clock = new TestClock(NOW, ClockConfig.ZONE);

    private final AtomicLong ids = new AtomicLong(1000);
    private final Map<Class<?>, Object> repositories = new HashMap<>();
    private SopCourseReadiness readiness;
    private TrainingAuditService audit;
    private SopVersionService versions;
    private SopGenerationQueue queue;
    private SopNewJoiners newJoiners;
    private TrainerAccessService trainerAccess;
    private TrainingRegistrationService sessionRegistrations;
    private final NotificationService notifier = new RecordingNotifications();

    /** Sets the service's repositories, clock and course readiness check to this store's. */
    <T> T wire(T service) {
        for (Class<?> c = service.getClass(); c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                Object value = REPOSITORIES.contains(f.getType()) ? repository(f.getType())
                        : f.getType() == Clock.class ? clock
                        : f.getType() == SopCourseReadiness.class ? readiness()
                        : f.getType() == TrainingAuditService.class ? audit()
                        : f.getType() == SopVersionService.class ? versions()
                        : f.getType() == SopGenerationQueue.class ? queue()
                        : f.getType() == SopNewJoiners.class ? newJoiners()
                        : f.getType() == TrainerAccessService.class ? trainerAccess()
                        : f.getType() == TrainingRegistrationService.class ? sessionRegistrations()
                        : f.getType() == NotificationService.class ? notifier
                        : null;
                if (value != null) {
                    ReflectionTestUtils.setField(service, f.getName(), value);
                }
            }
        }
        return service;
    }

    SopCourseReadiness readiness() {
        if (readiness == null) {
            readiness = new SopCourseReadiness();
            wire(readiness);
        }
        return readiness;
    }

    TrainingAuditService audit() {
        if (audit == null) {
            audit = wire(new TrainingAuditServiceImpl());
        }
        return audit;
    }

    SopVersionService versions() {
        if (versions == null) {
            versions = wire(new SopVersionServiceImpl());
        }
        return versions;
    }

    SopNewJoiners newJoiners() {
        if (newJoiners == null) {
            newJoiners = wire(new SopNewJoiners());
        }
        return newJoiners;
    }

    TrainerAccessService trainerAccess() {
        if (trainerAccess == null) {
            trainerAccess = wire(new TrainerAccessServiceImpl());
        }
        return trainerAccess;
    }

    TrainingRegistrationService sessionRegistrations() {
        if (sessionRegistrations == null) {
            sessionRegistrations = wire(new TrainingRegistrationServiceImpl());
        }
        return sessionRegistrations;
    }

    /** The generation queue, without a generation service: tests that run it set one. */
    SopGenerationQueue queue() {
        if (queue == null) {
            queue = wire(new SopGenerationQueue());
        }
        return queue;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    // --- fixtures ---------------------------------------------------------------

    OrgChart department(long id, String name) {
        OrgChart o = new OrgChart();
        o.setId(id);
        o.setName(name);
        o.setType(OrgChartType.D);
        orgCharts.put(id, o);
        return o;
    }

    Role role(long id, String name, OrgChart department) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        r.setVisible(true);
        r.setOrgChart(department);
        roles.put(id, r);
        return r;
    }

    /** Gives the role a permission, as the Roles page does. */
    void permit(Role role, AuthorityName name) {
        Authority authority = authorities.computeIfAbsent(name, n -> {
            Authority a = new Authority(n, n.name());
            a.setId(ids.incrementAndGet());
            return a;
        });
        roleAuthorities.add(new RoleAuthority(role, authority));
    }

    Staff person(String name, Role role) {
        Staff s = new Staff();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setEmail(name.toLowerCase().replace(' ', '.') + "@tbm.net");
        s.setRole(role);
        s.setAccountStatus(StaffAccountStatus.ACTIVE);
        staff.put(s.getId(), s);
        return s;
    }

    /** A generated course; a lower id is a newer upload. */
    SopDocument doc(long id, String title, UUID owner) {
        SopDocument d = new SopDocument();
        d.setId(id);
        d.setTitle(title);
        d.setUploadedBy(owner);
        d.setCourseId(id);
        d.setVisibility(SopVisibility.PRIVATE);
        d.setGenerationStatus(SopGenerationStatus.COMPLETED);
        d.setCreatedAt(now().minusDays(id));
        d.setUpdatedAt(now());
        docs.put(id, d);
        return d;
    }

    /** {@code count} questions, all {@code status}: a quiz for {@link #module(long, ReviewStatus, ReviewStatus...)}. */
    static ReviewStatus[] quiz(int count, ReviewStatus status) {
        ReviewStatus[] quiz = new ReviewStatus[count];
        Arrays.fill(quiz, status);
        return quiz;
    }

    /** A module with approved material and a quiz of {@code questionCount} approved questions. */
    SopModule module(long docId, int questionCount) {
        ReviewStatus[] quiz = new ReviewStatus[questionCount];
        Arrays.fill(quiz, ReviewStatus.APPROVED);
        return module(docId, ReviewStatus.APPROVED, quiz);
    }

    /**
     * The next module of the course, with one multiple-choice question per quiz status. Question i has
     * options Ai, Bi, Ci, Di and the answer Bi.
     */
    SopModule module(long docId, ReviewStatus material, ReviewStatus... quiz) {
        SopModule m = new SopModule();
        m.setId(ids.incrementAndGet());
        m.setSopDocumentId(docId);
        m.setModuleOrder((int) modules.values().stream().filter(x -> x.getSopDocumentId().equals(docId)).count() + 1);
        m.setTitle("Module " + m.getModuleOrder());
        m.setContent("{\"summary\":\"s\",\"sections\":[]}");
        m.setReviewStatus(material);
        modules.put(m.getId(), m);
        for (int i = 0; i < quiz.length; i++) {
            SopQuizQuestion q = new SopQuizQuestion();
            q.setId(ids.incrementAndGet());
            q.setSopModuleId(m.getId());
            q.setQuestionType(QuizQuestionType.MULTIPLE_CHOICE);
            q.setQuestionText("Question " + (i + 1) + "?");
            q.setOptions("[\"A" + i + "\",\"B" + i + "\",\"C" + i + "\",\"D" + i + "\"]");
            q.setCorrectAnswer("B" + i);
            q.setExplanation("Because B" + i);
            q.setReviewStatus(quiz[i]);
            q.setCreatedAt(now());
            q.setUpdatedAt(now());
            questions.put(q.getId(), q);
        }
        return m;
    }

    List<SopQuizQuestion> questionsOf(Long moduleId) {
        return questions.values().stream().filter(q -> q.getSopModuleId().equals(moduleId))
                .sorted(Comparator.comparing(SopQuizQuestion::getId)).toList();
    }

    SopEnrollment enrollment(long docId, UUID staffId, SopAssignmentMode mode, LocalDate effective, LocalDate deadline,
                             int daysToComplete, int passMark) {
        SopEnrollment e = new SopEnrollment();
        e.setId(ids.incrementAndGet());
        e.setSopDocumentId(docId);
        e.setStaffId(staffId);
        e.setAssignmentId(1L);
        e.setMode(mode);
        e.setEffectiveDate(effective);
        e.setDeadline(deadline);
        e.setDaysToComplete(daysToComplete);
        e.setPassMark(passMark);
        e.setStatus(SopEnrollmentStatus.NOT_STARTED);
        e.setAssignedAt(now());
        e.setUpdatedAt(now());
        enrollments.put(e.getId(), e);
        return e;
    }

    SopEnrollment enrollmentOf(long docId, UUID staffId) {
        return enrollments.values().stream()
                .filter(e -> e.getSopDocumentId().equals(docId) && e.getStaffId().equals(staffId))
                .findFirst().orElseThrow();
    }

    /** A recorded quiz attempt on a 4-question quiz. */
    SopQuizAttempt attempt(SopEnrollment e, Long moduleId, int score, boolean passed, OffsetDateTime at) {
        SopQuizAttempt a = new SopQuizAttempt();
        a.setId(ids.incrementAndGet());
        a.setEnrollmentId(e.getId());
        a.setSopModuleId(moduleId);
        a.setStaffId(e.getStaffId());
        a.setScore(score);
        a.setCorrect(score / 25);
        a.setTotal(4);
        a.setPassed(passed);
        a.setAttemptedAt(at);
        attempts.put(a.getId(), a);
        return a;
    }

    // --- repositories -----------------------------------------------------------

    @SuppressWarnings("unchecked")
    <T> T repository(Class<T> type) {
        return (T) repositories.computeIfAbsent(type, t -> Proxy.newProxyInstance(t.getClassLoader(), new Class<?>[] {t},
                (proxy, method, a) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == a[0];
                    case "toString" -> t.getSimpleName();
                    default -> call(t, method.getName(), a);
                }));
    }

    @SuppressWarnings("unchecked")
    private Object call(Class<?> type, String method, Object[] a) {
        switch (method) {
            case "save":
                return save(a[0]);
            case "saveAll":
                ((Iterable<?>) a[0]).forEach(this::save);
                return a[0];
            case "findById":
                return Optional.ofNullable(table(type).get(a[0]));
            case "findAll":
                return new ArrayList<>(table(type).values());
            case "findAllById": {
                Set<Object> wanted = new HashSet<>();
                ((Iterable<?>) a[0]).forEach(wanted::add);
                return table(type).entrySet().stream().filter(e -> wanted.contains(e.getKey())).map(Map.Entry::getValue).toList();
            }
            case "delete":
                table(type).values().remove(a[0]);
                return null;
            default:
        }
        if (type == SopDocumentRepository.class && method.equals("findAllByIsDeletedFalseOrderByCreatedAtDesc")) {
            return docs.values().stream().filter(d -> !d.isDeleted())
                    .sorted(Comparator.comparing(SopDocument::getCreatedAt).reversed()).toList();
        }
        if (type == SopDocumentRepository.class && method.equals("findAllByIsDeletedFalseAndGenerationStatusInOrderByCreatedAtAsc")) {
            return docs.values().stream().filter(d -> !d.isDeleted() && ((Collection<?>) a[0]).contains(d.getGenerationStatus()))
                    .sorted(Comparator.comparing(SopDocument::getCreatedAt)).toList();
        }
        if (type == SopDocumentRepository.class && method.equals("findVersions")) {
            return docs.values().stream().filter(d -> !d.isDeleted() && (a[0].equals(d.getCourseId()) || a[0].equals(d.getId())))
                    .sorted(Comparator.comparing(SopDocument::getVersionNo)).toList();
        }
        if (type == SopModuleRepository.class) {
            switch (method) {
                case "findAllBySopDocumentIdOrderByModuleOrderAsc":
                    return modulesOf(List.of((Long) a[0]));
                case "findAllBySopDocumentIdInOrderByModuleOrderAsc":
                    return modulesOf((Collection<Long>) a[0]);
                case "deleteAllBySopDocumentId":
                    modules.values().removeIf(m -> m.getSopDocumentId().equals(a[0]));
                    return null;
                default:
            }
        }
        if (type == SopQuizQuestionRepository.class) {
            switch (method) {
                case "findAllBySopModuleIdOrderByIdAsc":
                    return questionsOf((Long) a[0]);
                case "findAllBySopModuleIdInOrderByIdAsc":
                    return questions.values().stream().filter(q -> ((Collection<Long>) a[0]).contains(q.getSopModuleId()))
                            .sorted(Comparator.comparing(SopQuizQuestion::getId)).toList();
                case "deleteAllBySopModuleIdIn":
                    questions.values().removeIf(q -> ((Collection<Long>) a[0]).contains(q.getSopModuleId()));
                    return null;
                default:
            }
        }
        if (type == SopQuizReviewEventRepository.class) {
            switch (method) {
                case "findAllBySopModuleIdOrderByCreatedAtDescIdDesc":
                    return events.values().stream().filter(e -> e.getSopModuleId().equals(a[0]))
                            .sorted(Comparator.comparing(SopQuizReviewEvent::getCreatedAt)
                                    .thenComparing(SopQuizReviewEvent::getId).reversed()).toList();
                case "deleteAllBySopModuleIdIn":
                    events.values().removeIf(e -> ((Collection<Long>) a[0]).contains(e.getSopModuleId()));
                    return null;
                default:
            }
        }
        if (type == SopMaterialReviewEventRepository.class) {
            switch (method) {
                case "findAllBySopModuleIdOrderByCreatedAtDescIdDesc":
                    return materialEvents.values().stream().filter(e -> e.getSopModuleId().equals(a[0]))
                            .sorted(Comparator.comparing(SopMaterialReviewEvent::getCreatedAt)
                                    .thenComparing(SopMaterialReviewEvent::getId).reversed()).toList();
                case "deleteAllBySopModuleIdIn":
                    materialEvents.values().removeIf(e -> ((Collection<Long>) a[0]).contains(e.getSopModuleId()));
                    return null;
                default:
            }
        }
        if (type == SopAssignmentRepository.class) {
            switch (method) {
                case "findAllBySopDocumentIdInOrderByCreatedAtDesc":
                    return assignments.values().stream().filter(x -> ((Collection<Long>) a[0]).contains(x.getSopDocumentId()))
                            .sorted(Comparator.comparing(SopAssignment::getCreatedAt).thenComparing(SopAssignment::getId).reversed())
                            .toList();
                case "deleteAllBySopDocumentId":
                    assignments.values().removeIf(x -> x.getSopDocumentId().equals(a[0]));
                    return null;
                case "findAllByIncludeNewJoinersTrueOrderByCreatedAtDesc":
                    return assignments.values().stream().filter(SopAssignment::isIncludeNewJoiners)
                            .sorted(Comparator.comparing(SopAssignment::getCreatedAt).thenComparing(SopAssignment::getId).reversed())
                            .toList();
                default:
            }
        }
        if (type == SopEnrollmentRepository.class) {
            switch (method) {
                case "findAllBySopDocumentId":
                    return enrollments.values().stream().filter(e -> e.getSopDocumentId().equals(a[0])).toList();
                case "findAllBySopDocumentIdIn":
                    return enrollments.values().stream().filter(e -> ((Collection<Long>) a[0]).contains(e.getSopDocumentId())).toList();
                case "findAllByStaffId":
                    return enrollments.values().stream().filter(e -> e.getStaffId().equals(a[0])).toList();
                case "findBySopDocumentIdAndStaffId":
                    return enrollments.values().stream()
                            .filter(e -> e.getSopDocumentId().equals(a[0]) && e.getStaffId().equals(a[1])).findFirst();
                case "deleteAllBySopDocumentId":
                    enrollments.values().removeIf(e -> e.getSopDocumentId().equals(a[0]));
                    return null;
                default:
            }
        }
        if (type == SopQuizDrawRepository.class || type == SopMaterialViewRepository.class) {
            Map<Long, ?> rows = type == SopQuizDrawRepository.class ? draws : views;
            switch (method) {
                case "findByEnrollmentIdAndSopModuleId":
                    return rows.values().stream().filter(r -> enrollmentOf(r).equals(a[0]) && moduleOf(r).equals(a[1])).findFirst();
                case "deleteAllByEnrollmentIdIn":
                    rows.values().removeIf(r -> ((Collection<Long>) a[0]).contains(enrollmentOf(r)));
                    return null;
                default:
            }
        }
        if (type == SopQuizAttemptRepository.class) {
            Comparator<SopQuizAttempt> order = Comparator.comparing(SopQuizAttempt::getAttemptedAt).thenComparing(SopQuizAttempt::getId);
            switch (method) {
                case "findAllByEnrollmentIdOrderByAttemptedAtAscIdAsc":
                    return attempts.values().stream().filter(x -> x.getEnrollmentId().equals(a[0])).sorted(order).toList();
                case "findAllByEnrollmentIdInOrderByAttemptedAtAscIdAsc":
                    return attempts.values().stream().filter(x -> ((Collection<Long>) a[0]).contains(x.getEnrollmentId()))
                            .sorted(order).toList();
                case "deleteAllByEnrollmentIdIn":
                    attempts.values().removeIf(x -> ((Collection<Long>) a[0]).contains(x.getEnrollmentId()));
                    return null;
                default:
            }
        }
        if (type == SopCourseFeedbackRepository.class) {
            switch (method) {
                case "findBySopDocumentIdAndStaffId":
                    return feedback.values().stream()
                            .filter(f -> f.getSopDocumentId().equals(a[0]) && f.getStaffId().equals(a[1])).findFirst();
                case "deleteAllBySopDocumentId":
                    feedback.values().removeIf(f -> f.getSopDocumentId().equals(a[0]));
                    return null;
                default:
            }
        }
        if (type == StaffRepository.class) {
            switch (method) {
                case "findAllActiveWithRole":
                    return staff.values().stream().filter(s -> s.getAccountStatus() == StaffAccountStatus.ACTIVE).toList();
                case "findAllWithRoleByIdIn":
                    return staff.values().stream().filter(s -> ((Collection<UUID>) a[0]).contains(s.getId())).toList();
                case "findByIdAndIsDeletedFalse":
                    return Optional.ofNullable(staff.get(a[0])).filter(s -> !s.isDeleted());
                case "findAllByIsDeletedIsFalseAndIdIn":
                    return staff.values().stream().filter(s -> !s.isDeleted() && ((Collection<?>) a[0]).contains(s.getId())).toList();
                default:
            }
        }
        if (type == TrainingProgramRepository.class) {
            switch (method) {
                case "findAllByDeletedFalseAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc":
                    return sessions.values().stream()
                            .filter(x -> !x.isDeleted() && !x.getSessionDate().isBefore((LocalDate) a[0])
                                    && !x.getSessionDate().isAfter((LocalDate) a[1]))
                            .sorted(Comparator.comparing(TrainingProgram::getSessionDate).thenComparing(TrainingProgram::getStartTime))
                            .toList();
                case "findByTrainingIdAndDeletedFalse":
                case "lockById":
                    return Optional.ofNullable(sessions.get(a[0])).filter(x -> !x.isDeleted());
                default:
            }
        }
        if (type == TrainingRegistrationRepository.class) {
            switch (method) {
                case "findAllByTrainingId":
                    return registrations.values().stream().filter(r -> r.getTrainingId().equals(a[0])).toList();
                case "findAllByTrainingIdIn":
                    return registrations.values().stream().filter(r -> ((Collection<?>) a[0]).contains(r.getTrainingId())).toList();
                case "existsByTrainingId":
                    return registrations.values().stream().anyMatch(r -> r.getTrainingId().equals(a[0]));
                case "findByTrainingIdAndStaffId":
                    return registrations.values().stream()
                            .filter(r -> r.getTrainingId().equals(a[0]) && r.getStaffId().equals(a[1])).findFirst();
                case "findAllByStaffIdAndStatusIn":
                    return registrations.values().stream()
                            .filter(r -> r.getStaffId().equals(a[0]) && ((Collection<?>) a[1]).contains(r.getStatus())).toList();
                case "findAllByStatusAndOfferExpiresAtLessThanEqual":
                    return registrations.values().stream().filter(r -> r.getStatus() == a[0] && r.getOfferExpiresAt() != null
                            && !r.getOfferExpiresAt().isAfter((LocalDateTime) a[1])).toList();
                default:
            }
        }
        if (type == TrainingInvitationRepository.class) {
            switch (method) {
                case "findAllByTrainingIdAndStatus":
                    return invitations.values().stream().filter(i -> i.getTrainingId().equals(a[0]) && i.getStatus() == a[1]).toList();
                case "existsByTrainingId":
                    return invitations.values().stream().anyMatch(i -> i.getTrainingId().equals(a[0]));
                case "findAllByTrainingId":
                    return invitations.values().stream().filter(i -> i.getTrainingId().equals(a[0])).toList();
                case "findByTrainingIdAndStaffId":
                    return invitations.values().stream()
                            .filter(i -> i.getTrainingId().equals(a[0]) && i.getStaffId().equals(a[1])).findFirst();
                case "findAllByStaffIdAndTrainingIdIn":
                    return invitations.values().stream()
                            .filter(i -> i.getStaffId().equals(a[0]) && ((Collection<?>) a[1]).contains(i.getTrainingId())).toList();
                case "findAllByStaffIdAndStatus":
                    return invitations.values().stream().filter(i -> i.getStaffId().equals(a[0]) && i.getStatus() == a[1]).toList();
                case "findInvitedTrainingIds":
                    return invitations.values().stream().map(TrainingInvitation::getTrainingId)
                            .filter(((Collection<?>) a[0])::contains).distinct().toList();
                default:
            }
        }
        if (type == TrainingAttendanceRepository.class) {
            switch (method) {
                case "existsByTrainingId":
                    return attendance.values().stream().anyMatch(x -> x.getTrainingId().equals(a[0]));
                case "findRecordedTrainingIds":
                    return attendance.values().stream().map(TrainingAttendance::getTrainingId)
                            .filter(((Collection<?>) a[0])::contains).distinct().toList();
                default:
            }
        }
        if (type == TrainingAuditLogRepository.class && method.equals("findAllByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc")) {
            return auditLog.values().stream().filter(e -> e.getTargetType().equals(a[0]) && e.getTargetId().equals(a[1]))
                    .sorted(Comparator.comparing(TrainingAuditLog::getCreatedAt).thenComparing(TrainingAuditLog::getId).reversed())
                    .toList();
        }
        if (type == TrainerAccessGrantRepository.class && method.equals("findAllByRevokedAtIsNullAndLapsedAtIsNullAndEndDateBefore")) {
            return grants.values().stream().filter(g -> g.getRevokedAt() == null && g.getLapsedAt() == null
                    && g.getEndDate() != null && g.getEndDate().isBefore((LocalDate) a[0])).toList();
        }
        if (type == AuthorityRepository.class && method.equals("findByName")) {
            return Optional.ofNullable(authorities.get(a[0]));
        }
        if (type == RoleAuthorityRepository.class && method.equals("findAllByRoleIdIn")) {
            return roleAuthorities.stream().filter(ra -> ((Collection<Long>) a[0]).contains(ra.getId().getRoleId())).toList();
        }
        throw new UnsupportedOperationException(type.getSimpleName() + "." + method);
    }

    private List<SopModule> modulesOf(Collection<Long> docIds) {
        return modules.values().stream().filter(m -> docIds.contains(m.getSopDocumentId()))
                .sorted(Comparator.comparing(SopModule::getSopDocumentId).thenComparing(SopModule::getModuleOrder)).toList();
    }

    private Map<?, ?> table(Class<?> type) {
        if (type == SopDocumentRepository.class) return docs;
        if (type == SopModuleRepository.class) return modules;
        if (type == SopQuizQuestionRepository.class) return questions;
        if (type == SopQuizReviewEventRepository.class) return events;
        if (type == SopMaterialReviewEventRepository.class) return materialEvents;
        if (type == SopAssignmentRepository.class) return assignments;
        if (type == SopEnrollmentRepository.class) return enrollments;
        if (type == SopQuizAttemptRepository.class) return attempts;
        if (type == SopCourseFeedbackRepository.class) return feedback;
        if (type == SopQuizDrawRepository.class) return draws;
        if (type == SopMaterialViewRepository.class) return views;
        if (type == TrainerAccessGrantRepository.class) return grants;
        if (type == TrainingAuditLogRepository.class) return auditLog;
        if (type == OrgChartRepository.class) return orgCharts;
        if (type == RoleRepository.class) return roles;
        if (type == TrainingProgramRepository.class) return sessions;
        if (type == TrainingRegistrationRepository.class) return registrations;
        if (type == TrainingInvitationRepository.class) return invitations;
        if (type == TrainingAttendanceRepository.class) return attendance;
        return staff;
    }

    private Object save(Object entity) {
        if (entity instanceof SopDocument x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            docs.put(x.getId(), x);
        } else if (entity instanceof SopModule x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            modules.put(x.getId(), x);
        } else if (entity instanceof SopQuizQuestion x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            questions.put(x.getId(), x);
        } else if (entity instanceof SopQuizReviewEvent x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            events.put(x.getId(), x);
        } else if (entity instanceof SopMaterialReviewEvent x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            materialEvents.put(x.getId(), x);
        } else if (entity instanceof SopAssignment x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            assignments.put(x.getId(), x);
        } else if (entity instanceof SopEnrollment x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            enrollments.put(x.getId(), x);
        } else if (entity instanceof SopQuizAttempt x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            attempts.put(x.getId(), x);
        } else if (entity instanceof SopQuizDraw x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            draws.put(x.getId(), x);
        } else if (entity instanceof SopMaterialView x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            views.put(x.getId(), x);
        } else if (entity instanceof SopCourseFeedback x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            feedback.put(x.getId(), x);
        } else if (entity instanceof TrainerAccessGrant x) {
            grants.put(x.getStaffId(), x);
        } else if (entity instanceof TrainingAuditLog x) {
            if (x.getId() == null) x.setId(ids.incrementAndGet());
            auditLog.put(x.getId(), x);
        } else if (entity instanceof TrainingProgram x) {
            if (x.getTrainingId() == null) x.setTrainingId(ids.incrementAndGet());
            sessions.put(x.getTrainingId(), x);
        } else if (entity instanceof TrainingRegistration x) {
            if (x.getRegistrationId() == null) x.setRegistrationId(ids.incrementAndGet());
            registrations.put(x.getRegistrationId(), x);
        } else if (entity instanceof TrainingInvitation x) {
            if (x.getInvitationId() == null) x.setInvitationId(ids.incrementAndGet());
            invitations.put(x.getInvitationId(), x);
        } else if (entity instanceof TrainingAttendance x) {
            if (x.getAttendanceId() == null) x.setAttendanceId(ids.incrementAndGet());
            attendance.put(x.getAttendanceId(), x);
        } else {
            throw new UnsupportedOperationException("save " + entity.getClass().getSimpleName());
        }
        return entity;
    }

    private static Long enrollmentOf(Object row) {
        return row instanceof SopQuizDraw d ? d.getEnrollmentId() : ((SopMaterialView) row).getEnrollmentId();
    }

    private static Long moduleOf(Object row) {
        return row instanceof SopQuizDraw d ? d.getSopModuleId() : ((SopMaterialView) row).getSopModuleId();
    }

    /** A notification a service sent. */
    record Sent(UUID recipient, NotificationType type, String title, String body, String link) {
    }

    /** Records notifications instead of saving and emailing them. */
    private final class RecordingNotifications implements NotificationService {
        private final Set<String> keys = new HashSet<>();

        @Override
        public void notify(UUID recipientId, NotificationType type, String title, String body, String link) {
            notifications.add(new Sent(recipientId, type, title, body, link));
        }

        @Override
        public boolean notifyOnce(String dedupeKey, UUID recipientId, NotificationType type, String title, String body,
                                  String link) {
            if (!keys.add(dedupeKey)) {
                return false;
            }
            notify(recipientId, type, title, body, link);
            return true;
        }

        @Override
        public NotificationListDto mine(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long unreadCount(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markRead(Long notificationId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markAllRead(UUID userId) {
            throw new UnsupportedOperationException();
        }
    }

    /** A clock the test can move; every service wired to the store sees the change. */
    static final class TestClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        TestClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
