package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopFeedbackRequest;
import com.tbm.careerpathlearning.dto.SopLearningCourseDetailDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.dto.SopLearningHomeDto;
import com.tbm.careerpathlearning.dto.SopLearningQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizResultDto;
import com.tbm.careerpathlearning.dto.SopQuizSubmission;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SopLearningServiceImplTest {

    private SopTestStore store;
    private SopLearningServiceImpl service;
    private Staff learner;
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        service = store.wire(new SopLearningServiceImpl());
        learner = store.person("Nur Aisyah", null);
    }

    @Test
    @DisplayName("Modules unlock in order, and only enrolled staff can open the course")
    void modulesUnlockInOrder() {
        List<Long> modules = assignedCourse();

        SopLearningCourseDetailDto course = service.course(1L, learner.getId());

        assertEquals(List.of("AVAILABLE", "LOCKED", "LOCKED"), states(course));
        assertEquals(SopEnrollmentStatus.NOT_STARTED, course.getCourse().getStatus());
        assertThrows(BadRequestException.class, () -> service.material(modules.get(1), learner.getId()));
        assertThrows(ForbiddenRequestException.class, () -> service.course(1L, stranger));
    }

    @Test
    @DisplayName("A failed quiz goes to retraining, a retake can pass, and passing every module completes the course")
    void failRetakeAndComplete() {
        List<Long> modules = assignedCourse();
        Long first = modules.get(0);
        SopEnrollment enrollment = store.enrollmentOf(1L, learner.getId());

        service.start(1L, learner.getId());
        assertEquals(SopEnrollmentStatus.IN_PROGRESS, enrollment.getStatus());
        assertNotNull(enrollment.getStartedAt());
        assertTrue(service.material(first, learner.getId()).getContent().contains("sections"));
        List<SopLearningQuestionDto> quiz = service.quiz(first, learner.getId());
        assertEquals(4, quiz.size());
        assertEquals(4, quiz.get(0).getOptions().size());

        SopQuizResultDto fail = service.submit(first, answers(first, 1), learner.getId());
        assertEquals(25, fail.getScore());
        assertFalse(fail.isPassed(), "25% is below the pass mark of 70");
        assertEquals("RETRAINING", fail.getModuleState());
        assertEquals(1, fail.getAttempts());
        assertTrue(fail.getQuestions().stream().allMatch(q -> q.getCorrectAnswer() == null && q.getExplanation() == null),
                "a failed attempt must not reveal the answers");
        assertTrue(service.course(1L, learner.getId()).getCourse().isRetraining());

        SopQuizResultDto pass = service.submit(first, answers(first, 3), learner.getId());
        assertTrue(pass.isPassed());
        assertEquals(75, pass.getScore());
        assertEquals(2, pass.getAttempts());
        assertTrue(pass.getQuestions().stream().allMatch(q -> q.getCorrectAnswer() != null), "a pass reveals the answers");
        assertEquals(modules.get(1), pass.getNextModuleId());
        assertThrows(BadRequestException.class, () -> service.quiz(first, learner.getId()), "a passed quiz cannot be retaken");

        service.submit(modules.get(1), answers(modules.get(1), 2), learner.getId());
        SopQuizResultDto lastFail = service.submit(modules.get(2), answers(modules.get(2), 1), learner.getId());
        assertFalse(lastFail.isPassed());
        assertFalse(lastFail.isCourseCompleted());
        SopQuizResultDto done = service.submit(modules.get(2), answers(modules.get(2), 2), learner.getId());
        assertTrue(done.isCourseCompleted());
        assertEquals(92, done.getFinalScore(), "the average of the passing scores 75, 100 and 100");
        assertEquals(92, enrollment.getFinalScore());

        SopLearningCourseDetailDto finished = service.course(1L, learner.getId());
        assertEquals(String.format("APEX-SOP-1-%05d", enrollment.getId()), finished.getCertificate().getCertificateNo());
        assertEquals("Nur Aisyah", finished.getCertificate().getLearnerName());
        var firstModule = finished.getModules().get(0);
        assertEquals(2, firstModule.getAttempts());
        assertEquals(1, firstModule.getFailedAttempts());
        assertEquals(75, firstModule.getPassedScore());
    }

    @Test
    @DisplayName("Feedback is taken only after completing the course, rated 1 to 5")
    void feedbackAfterCompleting() {
        List<Long> modules = assignedCourse();
        service.start(1L, learner.getId());
        assertThrows(BadRequestException.class, () -> service.feedback(1L, rating(4), learner.getId()),
                "a course in progress cannot be rated");

        for (Long id : modules) {
            service.submit(id, answers(id, store.questionsOf(id).size()), learner.getId());
        }
        assertThrows(BadRequestException.class, () -> service.feedback(1L, rating(6), learner.getId()));
        SopLearningCourseDetailDto saved = service.feedback(1L, rating(5), learner.getId());

        assertEquals(5, saved.getFeedbackRating());
        assertEquals("Clear and useful", saved.getFeedbackComment());
    }

    @Test
    @DisplayName("An Open course is listed in the portal, and joining it moves it to My courses")
    void enrolInOpenCourse() {
        store.doc(2L, "Forklift Safety", null).setVisibility(SopVisibility.OPEN);
        store.module(2L, 2);

        SopLearningHomeDto home = service.home(learner.getId());
        assertEquals(1, home.getOpenCourses().size());
        assertEquals(2L, home.getOpenCourses().get(0).getSopDocumentId());

        SopLearningCourseDto joined = service.enrol(2L, learner.getId());
        assertEquals(SopAssignmentMode.PORTAL, joined.getMode());
        assertEquals(70, joined.getPassMark());
        assertNull(joined.getDueDate());
        assertTrue(joined.isSelfEnrolled());

        home = service.home(learner.getId());
        assertTrue(home.getOpenCourses().isEmpty());
        assertEquals(1, home.getCourses().size());
    }

    @Test
    @DisplayName("A course that opens later is shown as not yet available and cannot be started")
    void courseOpeningLater() {
        store.doc(3L, "Data Privacy", null);
        store.module(3L, 2);
        store.enrollment(3L, learner.getId(), SopAssignmentMode.PORTAL, store.today().plusDays(7),
                store.today().plusDays(30), 14, 70);

        assertFalse(service.course(3L, learner.getId()).getCourse().isAvailable());
        assertThrows(BadRequestException.class, () -> service.start(3L, learner.getId()));
    }

    @Test
    @DisplayName("A course opens on its effective date in Malaysia, even while it is still the day before in UTC")
    void opensOnTheMalaysiaDate() {
        store.clock.set(Instant.parse("2026-10-12T17:00:00Z")); // 13 Oct, 01:00 in Malaysia
        store.doc(3L, "Data Privacy", null);
        store.module(3L, 2);
        store.enrollment(3L, learner.getId(), SopAssignmentMode.PORTAL, LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 11, 30), 14, 70);

        assertTrue(service.course(3L, learner.getId()).getCourse().isAvailable());
        assertDoesNotThrow(() -> service.start(3L, learner.getId()));
    }

    @Test
    @DisplayName("A course is overdue once its due date has passed")
    void overdue() {
        store.doc(4L, "E-Hailing", null);
        store.module(4L, 2);
        store.enrollment(4L, learner.getId(), SopAssignmentMode.IMMEDIATE, store.today().minusDays(20),
                store.today().plusDays(30), 14, 70);

        assertTrue(service.course(4L, learner.getId()).getCourse().isOverdue());
    }

    @Test
    @DisplayName("A module whose quiz is no longer approved is unavailable and blocks completion")
    void unapprovedQuizBlocksCompletion() {
        store.doc(4L, "E-Hailing", null);
        SopModule first = store.module(4L, 2);
        SopModule second = store.module(4L, 2);
        store.enrollment(4L, learner.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(30), 14, 70);
        store.questionsOf(second.getId()).get(0).setReviewStatus(ReviewStatus.PENDING); // the trainer edited the quiz

        service.submit(first.getId(), answers(first.getId(), 2), learner.getId());
        SopLearningCourseDetailDto course = service.course(4L, learner.getId());

        assertEquals(List.of("PASSED", "UNAVAILABLE"), states(course));
        assertNotEquals(SopEnrollmentStatus.COMPLETED, course.getCourse().getStatus());
        assertThrows(BadRequestException.class, () -> service.quiz(second.getId(), learner.getId()));
    }

    @Test
    @DisplayName("Fill-in-the-blank answers ignore case, spacing and end punctuation")
    void fillInTheBlank() {
        SopQuizQuestion q = new SopQuizQuestion();
        q.setQuestionType(QuizQuestionType.FILL_IN_THE_BLANK);
        q.setCorrectAnswer("12 hours");

        assertTrue(SopLearningServiceImpl.isCorrect(q, "  12   Hours. "));
        assertFalse(SopLearningServiceImpl.isCorrect(q, "24 hours"));
    }

    // --- fixtures ---------------------------------------------------------------

    /** Course 1, assigned to the learner from the portal: module 1 has 4 questions, modules 2 and 3 have 2 each. */
    private List<Long> assignedCourse() {
        store.doc(1L, "Cash Handling", null).setDepartmentTag("Sales");
        List<Long> modules = List.of(store.module(1L, 4).getId(), store.module(1L, 2).getId(), store.module(1L, 2).getId());
        store.enrollment(1L, learner.getId(), SopAssignmentMode.PORTAL, store.today(), store.today().plusDays(30), 14, 70);
        return modules;
    }

    /** Answers the module's questions, getting the first {@code right} of them correct. */
    private SopQuizSubmission answers(Long moduleId, int right) {
        Map<Long, String> answers = new HashMap<>();
        List<SopQuizQuestion> questions = store.questionsOf(moduleId);
        for (int i = 0; i < questions.size(); i++) {
            answers.put(questions.get(i).getId(), i < right ? questions.get(i).getCorrectAnswer() : "Wrong option");
        }
        SopQuizSubmission submission = new SopQuizSubmission();
        submission.setAnswers(answers);
        return submission;
    }

    private static SopFeedbackRequest rating(int stars) {
        SopFeedbackRequest request = new SopFeedbackRequest();
        request.setRating(stars);
        request.setComment("  Clear and useful  ");
        return request;
    }

    private static List<String> states(SopLearningCourseDetailDto course) {
        return course.getModules().stream().map(m -> m.getState()).toList();
    }
}
