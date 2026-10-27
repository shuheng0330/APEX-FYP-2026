package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.SopFeedbackRequest;
import com.tbm.careerpathlearning.dto.SopLearningCourseDetailDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.dto.SopLearningHomeDto;
import com.tbm.careerpathlearning.dto.SopLearningMaterialDto;
import com.tbm.careerpathlearning.dto.SopLearningModuleDto;
import com.tbm.careerpathlearning.dto.SopLearningQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizResultDto;
import com.tbm.careerpathlearning.dto.SopQuizSubmission;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopCourseFeedback;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopMaterialView;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizAttempt;
import com.tbm.careerpathlearning.model.SopQuizDraw;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.SopCourseFeedbackRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.SopMaterialViewRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizAttemptRepository;
import com.tbm.careerpathlearning.repository.SopQuizDrawRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import com.tbm.careerpathlearning.service.SopLearningService;
import com.tbm.careerpathlearning.service.SopProgress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
public class SopLearningServiceImpl implements SopLearningService {

    /** Pass mark for an Open course a learner joins themselves (no assignment sets one). */
    private static final int MAX_COMMENT_LENGTH = 2000;
    /** Tries at one module's quiz per Malaysia day (FR-11-04). */
    static final int TRIES_PER_DAY = 3;
    /** Failed attempts at one module that alert the course's trainer (FR-11-15). */
    static final int FAILS_TO_ALERT = 3;

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private SopQuizAttemptRepository sopQuizAttemptRepository;
    @Autowired private SopCourseFeedbackRepository sopCourseFeedbackRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private SopCourseReadiness sopCourseReadiness;
    @Autowired private SopQuizDrawRepository sopQuizDrawRepository;
    @Autowired private SopMaterialViewRepository sopMaterialViewRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private Clock clock;

    /** Draws questions and shuffles options; replaced in tests for a repeatable order. */
    private Random random = new Random();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // === portal home ==========================================================

    @Override
    public SopLearningHomeDto home(UUID staffId) {
        SopLearningHomeDto dto = new SopLearningHomeDto();
        Set<Long> enrolled = new HashSet<>();
        for (SopEnrollment e : sopEnrollmentRepository.findAllByStaffId(staffId)) {
            SopDocument doc = sopDocumentRepository.findById(e.getSopDocumentId()).orElse(null);
            if (doc == null || doc.isDeleted()) {
                continue;
            }
            if (e.getStatus() != SopEnrollmentStatus.WITHDRAWN) {
                // Any version counts: someone taking or finished with a course isn't offered it again. Someone
                // withdrawn can join an Open course again once it is restored.
                enrolled.add(doc.courseKey());
            }
            dto.getCourses().add(toCourseDto(progress(e, doc)));
        }
        dto.getCourses().sort(Comparator
                .comparing((SopLearningCourseDto c) -> c.getDueDate() == null ? LocalDate.MAX : c.getDueDate())
                .thenComparing(SopLearningCourseDto::getTitle, String.CASE_INSENSITIVE_ORDER));
        for (SopDocument doc : sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc()) {
            if (doc.isLive() && doc.getVisibility() == SopVisibility.OPEN && !enrolled.contains(doc.courseKey())) {
                SopCourseReadiness.Readiness readiness = sopCourseReadiness.of(doc);
                if (readiness.ready()) {
                    dto.getOpenCourses().add(openCourseDto(doc, readiness.modules()));
                }
            }
        }
        return dto;
    }

    @Override
    @Transactional
    public SopLearningCourseDto enrol(Long sopDocumentId, UUID staffId) {
        SopDocument doc = docOrThrow(sopDocumentId);
        if (!doc.isLive() || doc.getVisibility() != SopVisibility.OPEN || !sopCourseReadiness.of(doc).ready()) {
            throw new BadRequestException("This course is not open for enrolment.");
        }
        SopEnrollment e = sopEnrollmentRepository.findBySopDocumentIdAndStaffId(sopDocumentId, staffId).orElse(null);
        if (e == null || e.getStatus() == SopEnrollmentStatus.WITHDRAWN) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (e == null) {
                e = new SopEnrollment();
                e.setSopDocumentId(sopDocumentId);
                e.setStaffId(staffId);
            }
            // Joining, or joining again after being withdrawn: Open-course terms and a fresh start. Quiz attempts
            // already made still count (FR-06-15).
            e.setAssignmentId(null);
            e.setMode(SopAssignmentMode.PORTAL);
            e.setMandatory(false);
            e.setEffectiveDate(LocalDate.now(clock));
            e.setDeadline(null);
            e.setDaysToComplete(null);
            e.setPassMark(doc.getPassMark());
            e.setDurationMinutes(doc.effectiveDurationMinutes());
            e.setStatus(SopEnrollmentStatus.NOT_STARTED);
            e.setStartedAt(null);
            e.setAssignedAt(now);
            e.setUpdatedAt(now);
            e = sopEnrollmentRepository.save(e);
        }
        return toCourseDto(progress(e, doc));
    }

    // === course player =======================================================

    @Override
    public SopLearningCourseDetailDto course(Long sopDocumentId, UUID staffId) {
        SopDocument doc = docOrThrow(sopDocumentId);
        return detail(progress(enrollmentOrThrow(sopDocumentId, staffId), doc), staffId);
    }

    @Override
    @Transactional
    public SopLearningCourseDetailDto start(Long sopDocumentId, UUID staffId) {
        SopDocument doc = docOrThrow(sopDocumentId);
        SopEnrollment e = enrollmentOrThrow(sopDocumentId, staffId);
        checkAvailable(e, doc);
        ensureStarted(e);
        return detail(progress(e, doc), staffId);
    }

    @Override
    public SopLearningMaterialDto material(Long moduleId, UUID staffId) {
        ModuleContext c = moduleContext(moduleId, staffId);
        SopMaterialView view = sopMaterialViewRepository.findByEnrollmentIdAndSopModuleId(c.enrollment().getId(), moduleId)
                .orElseGet(() -> {
                    SopMaterialView created = new SopMaterialView();
                    created.setEnrollmentId(c.enrollment().getId());
                    created.setSopModuleId(moduleId);
                    return created;
                });
        view.setViewedAt(OffsetDateTime.now(clock));
        sopMaterialViewRepository.save(view);
        SopLearningMaterialDto dto = new SopLearningMaterialDto();
        dto.setModuleId(c.module().getId());
        dto.setModuleOrder(c.module().getModuleOrder());
        dto.setTitle(c.module().getTitle());
        dto.setContent(c.module().getContent());
        return dto;
    }

    @Override
    public List<SopLearningQuestionDto> quiz(Long moduleId, UUID staffId) {
        ModuleContext c = moduleContext(moduleId, staffId);
        checkQuizOpen(c.current());
        checkCanTry(c);
        List<SopQuizQuestion> pool = new ArrayList<>(c.current().questions());
        Collections.shuffle(pool, random); // a new order, and a new selection when the quiz has more (FR-11-14)
        List<SopQuizQuestion> drawn = pool.subList(0, quizSize(c.module(), pool.size()));
        SopQuizDraw draw = sopQuizDrawRepository.findByEnrollmentIdAndSopModuleId(c.enrollment().getId(), moduleId)
                .orElseGet(SopQuizDraw::new);
        draw.setEnrollmentId(c.enrollment().getId());
        draw.setSopModuleId(moduleId);
        draw.setQuestionIds(String.join(",", drawn.stream().map(q -> q.getId().toString()).toList()));
        draw.setCreatedAt(OffsetDateTime.now(clock));
        sopQuizDrawRepository.save(draw);
        return drawn.stream().map(q -> {
            SopLearningQuestionDto dto = new SopLearningQuestionDto();
            dto.setId(q.getId());
            dto.setType(q.getQuestionType());
            dto.setQuestion(q.getQuestionText());
            List<String> options = new ArrayList<>(readOptions(q.getOptions()));
            if (q.getQuestionType() == QuizQuestionType.MULTIPLE_CHOICE) {
                Collections.shuffle(options, random);
            }
            dto.setOptions(options);
            dto.setDifficulty(q.getDifficulty());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public SopQuizResultDto submit(Long moduleId, SopQuizSubmission submission, UUID staffId) {
        ModuleContext c = moduleContext(moduleId, staffId);
        checkQuizOpen(c.current());
        checkCanTry(c);
        SopEnrollment e = c.enrollment();
        ensureStarted(e);

        Map<Long, String> answers = submission == null || submission.getAnswers() == null ? Map.of() : submission.getAnswers();
        SopQuizDraw draw = sopQuizDrawRepository.findByEnrollmentIdAndSopModuleId(e.getId(), moduleId).orElse(null);
        List<SopQuizQuestion> questions = draw == null ? List.of() : drawnQuestions(draw, c.current().questions());
        if (questions.isEmpty()) {
            throw new BadRequestException("This quiz changed since you opened it. Open it again to start a new attempt.");
        }
        SopQuizResultDto result = new SopQuizResultDto();
        int correct = 0;
        for (SopQuizQuestion q : questions) {
            SopQuizResultDto.QuestionResult r = new SopQuizResultDto.QuestionResult();
            r.setQuestionId(q.getId());
            r.setGivenAnswer(answers.get(q.getId()));
            r.setCorrect(isCorrect(q, answers.get(q.getId())));
            correct += r.isCorrect() ? 1 : 0;
            result.getQuestions().add(r);
        }
        int score = Math.round(100f * correct / questions.size());
        int needed = passCorrect(e.getPassMark(), questions.size());
        boolean passed = correct >= needed; // the same rule the learner was shown (FR-11-12)

        SopQuizAttempt attempt = new SopQuizAttempt();
        attempt.setEnrollmentId(e.getId());
        attempt.setSopModuleId(moduleId);
        attempt.setStaffId(staffId);
        attempt.setCorrect(correct);
        attempt.setTotal(questions.size());
        attempt.setScore(score);
        attempt.setPassed(passed);
        attempt.setAnswers(writeAnswers(answers));
        attempt.setAttemptedAt(OffsetDateTime.now(clock));
        sopQuizAttemptRepository.save(attempt);
        sopQuizDrawRepository.delete(draw);

        // Recompute with the new attempt; the course completes when every module is passed.
        Progress after = progress(e, c.doc());
        if (after.course().allPassed() && e.getStatus() != SopEnrollmentStatus.COMPLETED) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            e.setStatus(SopEnrollmentStatus.COMPLETED);
            e.setCompletedAt(now);
            e.setFinalScore(after.course().finalScore());
            e.setUpdatedAt(now);
            sopEnrollmentRepository.save(e);
        }
        SopProgress.Module mp = after.course().of(moduleId);
        if (!passed && mp.failedAttempts() >= FAILS_TO_ALERT) {
            alertTrainer(c, mp.failedAttempts());
        }
        result.setScore(score);
        result.setCorrect(correct);
        result.setTotal(questions.size());
        result.setPassed(passed);
        result.setPassMark(e.getPassMark());
        result.setPassCorrect(needed);
        result.setTriesLeftToday(triesLeftToday(mp));
        result.setAttempts(mp.attempts().size());
        result.setModuleState(mp.state());
        result.setCourseCompleted(e.getStatus() == SopEnrollmentStatus.COMPLETED);
        result.setFinalScore(e.getFinalScore());
        result.setNextModuleId(after.course().nextAfter(moduleId));
        if (passed) {
            // Answers are only revealed once the module is passed (FR-11-13).
            for (int i = 0; i < questions.size(); i++) {
                result.getQuestions().get(i).setCorrectAnswer(questions.get(i).getCorrectAnswer());
                result.getQuestions().get(i).setExplanation(questions.get(i).getExplanation());
            }
        } else {
            // After a fail, only the score: not which answers were wrong (FR-11-13).
            result.getQuestions().clear();
        }
        return result;
    }

    @Override
    @Transactional
    public SopLearningCourseDetailDto feedback(Long sopDocumentId, SopFeedbackRequest request, UUID staffId) {
        SopDocument doc = docOrThrow(sopDocumentId);
        SopEnrollment e = enrollmentOrThrow(sopDocumentId, staffId);
        if (e.getStatus() != SopEnrollmentStatus.COMPLETED) {
            throw new BadRequestException("You can give feedback once you have completed the course.");
        }
        Integer rating = request == null ? null : request.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new BadRequestException("Choose a rating from 1 to 5 stars.");
        }
        String comment = request.getComment() == null ? null : request.getComment().strip();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException("Please keep the comment under " + MAX_COMMENT_LENGTH + " characters.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        SopCourseFeedback f = sopCourseFeedbackRepository.findBySopDocumentIdAndStaffId(sopDocumentId, staffId)
                .orElseGet(() -> {
                    SopCourseFeedback created = new SopCourseFeedback();
                    created.setSopDocumentId(sopDocumentId);
                    created.setStaffId(staffId);
                    created.setCreatedAt(now);
                    return created;
                });
        f.setRating(rating);
        f.setComment(comment == null || comment.isEmpty() ? null : comment);
        f.setUpdatedAt(now);
        sopCourseFeedbackRepository.save(f);
        return detail(progress(e, doc), staffId);
    }

    // === progress =============================================================

    /** A learner's enrolment in a course and their progress through its modules. */
    private record Progress(SopEnrollment enrollment, SopDocument doc, SopProgress course) {
    }

    private Progress progress(SopEnrollment e, SopDocument doc) {
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(doc.getId());
        return new Progress(e, doc, SopProgress.of(modules, sopCourseReadiness.quizzesOf(modules),
                sopQuizAttemptRepository.findAllByEnrollmentIdOrderByAttemptedAtAscIdAsc(e.getId())));
    }

    private record ModuleContext(SopModule module, SopDocument doc, SopEnrollment enrollment, SopProgress.Module current) {
    }

    private ModuleContext moduleContext(Long moduleId, UUID staffId) {
        SopModule module = sopModuleRepository.findById(moduleId)
                .orElseThrow(() -> new BadRequestException("Module not found."));
        SopDocument doc = docOrThrow(module.getSopDocumentId());
        SopEnrollment e = enrollmentOrThrow(doc.getId(), staffId);
        checkAvailable(e, doc);
        SopProgress.Module current = progress(e, doc).course().of(moduleId);
        if (SopProgress.LOCKED.equals(current.state())) {
            throw new BadRequestException("Pass the previous module's quiz to unlock this module.");
        }
        if (SopProgress.UNAVAILABLE.equals(current.state())) {
            throw new BadRequestException("This module is being updated by the trainer and is not available yet.");
        }
        return new ModuleContext(module, doc, e, current);
    }

    /** Questions an attempt presents: the module's setting, or every question when the quiz has fewer (FR-08-15). */
    private static int quizSize(SopModule module, int questions) {
        return Math.min(Math.max(module.getQuestionsPerAttempt(), 1), questions);
    }

    /** Correct answers needed to reach the pass mark: 4 of 5 at 70% (FR-11-12). */
    static int passCorrect(int passMark, int questions) {
        return (passMark * questions + 99) / 100;
    }

    /** The drawn questions that still exist, in the order they were shown. */
    private static List<SopQuizQuestion> drawnQuestions(SopQuizDraw draw, List<SopQuizQuestion> current) {
        Map<Long, SopQuizQuestion> byId = new HashMap<>();
        current.forEach(q -> byId.put(q.getId(), q));
        return Arrays.stream(draw.getQuestionIds().split(",")).filter(id -> !id.isBlank())
                .map(id -> byId.get(Long.valueOf(id.trim()))).filter(Objects::nonNull).toList();
    }

    private int triesToday(SopProgress.Module module) {
        LocalDate today = LocalDate.now(clock);
        return (int) module.attempts().stream()
                .filter(a -> a.getAttemptedAt().atZoneSameInstant(clock.getZone()).toLocalDate().equals(today)).count();
    }

    private int triesLeftToday(SopProgress.Module module) {
        return Math.max(0, TRIES_PER_DAY - triesToday(module));
    }

    /** The last attempt failed and the learner hasn't opened the material since (FR-11-04). */
    private boolean reviewMaterialFirst(SopEnrollment e, SopProgress.Module module) {
        List<SopQuizAttempt> tries = module.attempts();
        SopQuizAttempt last = tries.isEmpty() ? null : tries.get(tries.size() - 1);
        if (last == null || last.isPassed()) {
            return false;
        }
        return sopMaterialViewRepository.findByEnrollmentIdAndSopModuleId(e.getId(), module.module().getId())
                .map(v -> !v.getViewedAt().isAfter(last.getAttemptedAt())).orElse(true);
    }

    /** A retake needs the material reopened after the fail, and at most three tries a day (FR-11-04). */
    private void checkCanTry(ModuleContext c) {
        if (triesLeftToday(c.current()) == 0) {
            throw new BadRequestException("You've used today's " + TRIES_PER_DAY + " tries at this quiz. You can try again tomorrow.");
        }
        if (reviewMaterialFirst(c.enrollment(), c.current())) {
            throw new BadRequestException("Review the module's material again before you retake the quiz.");
        }
    }

    /** Tells the course's trainer, once per learner and module, that someone keeps failing a quiz (FR-11-15). */
    private void alertTrainer(ModuleContext c, int fails) {
        UUID trainer = c.doc().getUploadedBy();
        if (trainer == null) {
            return;
        }
        String learner = staffRepository.findById(c.enrollment().getStaffId()).map(Staff::getName).orElse("A learner");
        notificationService.notifyOnce("quiz-failed-3:" + c.enrollment().getId() + ":" + c.module().getId(), trainer,
                NotificationType.QUIZ_FAILED_THREE_TIMES, "A learner may need help",
                learner + " has failed the quiz of module " + c.module().getModuleOrder() + ", " + c.module().getTitle()
                        + ", " + fails + " times in " + c.doc().getTitle() + ".", "/sop/scoreboard");
    }

    private static void checkQuizOpen(SopProgress.Module current) {
        if (SopProgress.PASSED.equals(current.state())) {
            throw new BadRequestException("You have already passed this module's quiz.");
        }
    }

    /** The learner can work on the course: not withdrawn, the course not archived, and its effective date reached. */
    private void checkAvailable(SopEnrollment e, SopDocument doc) {
        if (e.getStatus() == SopEnrollmentStatus.WITHDRAWN) {
            throw new BadRequestException("You were withdrawn from this course, so it can't be continued.");
        }
        if (doc.isArchived()) {
            throw new BadRequestException("This course has been archived, so it can't be taken any more.");
        }
        if (!doc.isLive()) {
            throw new BadRequestException("A newer version has replaced this one, so it can't be taken any more.");
        }
        if (e.getEffectiveDate() != null && e.getEffectiveDate().isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("This course opens on "
                    + e.getEffectiveDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) + ".");
        }
    }

    private void ensureStarted(SopEnrollment e) {
        if (e.getStatus() == SopEnrollmentStatus.NOT_STARTED) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            e.setStatus(SopEnrollmentStatus.IN_PROGRESS);
            e.setStartedAt(now);
            e.setUpdatedAt(now);
            sopEnrollmentRepository.save(e);
        }
    }

    /** Choice answers must match an option exactly; fill-in-the-blank ignores case and spacing. */
    static boolean isCorrect(SopQuizQuestion q, String given) {
        if (given == null || q.getCorrectAnswer() == null) {
            return false;
        }
        if (q.getQuestionType() == QuizQuestionType.FILL_IN_THE_BLANK) {
            return normalise(given).equals(normalise(q.getCorrectAnswer()));
        }
        return given.strip().equals(q.getCorrectAnswer().strip());
    }

    private static String normalise(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[.!?,;:]+$", "").strip();
    }

    // === mapping ==============================================================

    private SopLearningCourseDetailDto detail(Progress p, UUID staffId) {
        SopLearningCourseDetailDto dto = new SopLearningCourseDetailDto();
        dto.setCourse(toCourseDto(p));
        String learner = staffRepository.findById(staffId).map(Staff::getName).orElse("Learner");
        dto.setLearnerName(learner);
        for (SopProgress.Module m : p.course().modules()) {
            SopLearningModuleDto md = new SopLearningModuleDto();
            md.setId(m.module().getId());
            md.setModuleOrder(m.module().getModuleOrder());
            md.setTitle(m.module().getTitle());
            md.setState(m.state());
            md.setQuestionCount(m.questions().size());
            md.setQuizSize(quizSize(m.module(), m.questions().size()));
            md.setPassCorrect(passCorrect(p.enrollment().getPassMark(), md.getQuizSize()));
            md.setTriesLeftToday(triesLeftToday(m));
            md.setReviewMaterialFirst(reviewMaterialFirst(p.enrollment(), m));
            md.setAttempts(m.attempts().size());
            md.setFailedAttempts(m.failedAttempts());
            md.setBestScore(m.bestScore());
            md.setPassedScore(m.passedScore());
            md.setPassedAt(m.passedAttempt() == null ? null : m.passedAttempt().getAttemptedAt());
            dto.getModules().add(md);
        }
        SopEnrollment e = p.enrollment();
        if (e.getStatus() == SopEnrollmentStatus.COMPLETED) {
            SopLearningCourseDetailDto.Certificate cert = new SopLearningCourseDetailDto.Certificate();
            cert.setCertificateNo(e.certificateNo());
            cert.setLearnerName(learner);
            cert.setCourseTitle(p.doc().getTitle());
            cert.setVersion(p.doc().getVersion());
            cert.setVersionNo(p.doc().getVersionNo());
            cert.setFinalScore(e.getFinalScore());
            cert.setPassMark(e.getPassMark());
            cert.setCompletedAt(e.getCompletedAt());
            dto.setCertificate(cert);
        }
        sopCourseFeedbackRepository.findBySopDocumentIdAndStaffId(p.doc().getId(), staffId).ifPresent(f -> {
            dto.setFeedbackRating(f.getRating());
            dto.setFeedbackComment(f.getComment());
        });
        return dto;
    }

    private SopLearningCourseDto toCourseDto(Progress p) {
        SopEnrollment e = p.enrollment();
        SopLearningCourseDto dto = baseCourseDto(p.doc(), p.course().modules().size());
        LocalDate today = LocalDate.now(clock);
        LocalDate due = e.dueDate();
        dto.setEnrollmentId(e.getId());
        dto.setStatus(e.getStatus());
        dto.setMode(e.getMode());
        dto.setMandatory(e.isMandatory());
        dto.setSelfEnrolled(e.getAssignmentId() == null);
        dto.setEffectiveDate(e.getEffectiveDate());
        dto.setDueDate(due);
        dto.setDeadline(e.getDeadline());
        dto.setDaysToComplete(e.getDaysToComplete());
        dto.setPassMark(e.getPassMark());
        dto.setModulesPassed(p.course().passed());
        dto.setRetraining(p.course().retraining());
        dto.setOverdue(e.isActive() && due != null && due.isBefore(today));
        dto.setAvailable(e.getStatus() != SopEnrollmentStatus.WITHDRAWN && p.doc().isLive()
                && (e.getEffectiveDate() == null || !e.getEffectiveDate().isAfter(today)));
        dto.setCourseArchived(p.doc().isArchived());
        dto.setCourseSuperseded(p.doc().isSuperseded());
        dto.setFinalScore(e.getFinalScore());
        dto.setStartedAt(e.getStartedAt());
        dto.setCompletedAt(e.getCompletedAt());
        return dto;
    }

    private SopLearningCourseDto openCourseDto(SopDocument doc, int moduleCount) {
        SopLearningCourseDto dto = baseCourseDto(doc, moduleCount);
        dto.setPassMark(doc.getPassMark());
        dto.setAvailable(true);
        return dto;
    }

    private static SopLearningCourseDto baseCourseDto(SopDocument doc, int moduleCount) {
        SopLearningCourseDto dto = new SopLearningCourseDto();
        dto.setSopDocumentId(doc.getId());
        dto.setTitle(doc.getTitle());
        dto.setVersion(doc.getVersion());
        dto.setVersionNo(doc.getVersionNo());
        dto.setCategory(doc.getDepartmentTag() == null || doc.getDepartmentTag().isBlank() ? "General" : doc.getDepartmentTag());
        dto.setModuleCount(moduleCount);
        return dto;
    }

    // === helpers ==============================================================

    private SopDocument docOrThrow(Long id) {
        SopDocument doc = id == null ? null : sopDocumentRepository.findById(id).orElse(null);
        if (doc == null || doc.isDeleted()) {
            throw new BadRequestException("Course not found.");
        }
        return doc;
    }

    private SopEnrollment enrollmentOrThrow(Long sopDocumentId, UUID staffId) {
        return sopEnrollmentRepository.findBySopDocumentIdAndStaffId(sopDocumentId, staffId)
                .orElseThrow(() -> new ForbiddenRequestException("You are not enrolled in this course."));
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeAnswers(Map<Long, String> answers) {
        try {
            Map<String, String> byId = new LinkedHashMap<>();
            answers.forEach((id, answer) -> byId.put(Objects.toString(id), answer));
            return objectMapper.writeValueAsString(byId);
        } catch (Exception e) {
            return null;
        }
    }
}
