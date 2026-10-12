package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizAttempt;
import com.tbm.careerpathlearning.model.SopQuizQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A learner's progress through a course's modules, worked out from their quiz attempts. The
 * Learning Portal and the Training Scoreboard both use it, so learners and trainers always see the
 * same module states.
 */
public record SopProgress(List<Module> modules) {

    public static final String LOCKED = "LOCKED";
    public static final String AVAILABLE = "AVAILABLE";
    public static final String RETRAINING = "RETRAINING";
    public static final String PASSED = "PASSED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    /** One module and the learner's attempts at its quiz, oldest first. */
    public record Module(SopModule module, List<SopQuizQuestion> questions, List<SopQuizAttempt> attempts,
                         String state, SopQuizAttempt passedAttempt) {

        public int failedAttempts() {
            return (int) attempts.stream().filter(a -> !a.isPassed()).count();
        }

        public Integer bestScore() {
            return attempts.stream().map(SopQuizAttempt::getScore).max(Integer::compare).orElse(null);
        }

        public Integer passedScore() {
            return passedAttempt == null ? null : passedAttempt.getScore();
        }
    }

    /**
     * Each module's state: passed once any attempt passed; otherwise unavailable until its material
     * and quiz are approved, locked until the previous module is passed (FR-11-01), in retraining
     * after a failed attempt (FR-10-04), else available.
     *
     * @param attempts the learner's attempts on this course, oldest first
     */
    public static SopProgress of(List<SopModule> modules, Map<Long, List<SopQuizQuestion>> quizzes,
                                 List<SopQuizAttempt> attempts) {
        Map<Long, List<SopQuizAttempt>> byModule = attempts.stream()
                .collect(Collectors.groupingBy(SopQuizAttempt::getSopModuleId, LinkedHashMap::new, Collectors.toList()));
        List<Module> out = new ArrayList<>();
        boolean previousPassed = true;
        for (SopModule m : modules) {
            List<SopQuizQuestion> questions = quizzes.getOrDefault(m.getId(), List.of());
            List<SopQuizAttempt> tries = byModule.getOrDefault(m.getId(), List.of());
            SopQuizAttempt pass = tries.stream().filter(SopQuizAttempt::isPassed).findFirst().orElse(null);
            boolean approved = m.getReviewStatus() == ReviewStatus.APPROVED && SopCourseReadiness.quizApproved(questions);
            String state = pass != null ? PASSED
                    : !approved ? UNAVAILABLE
                    : !previousPassed ? LOCKED
                    : !tries.isEmpty() ? RETRAINING
                    : AVAILABLE;
            out.add(new Module(m, questions, tries, state, pass));
            previousPassed = pass != null;
        }
        return new SopProgress(out);
    }

    public int passed() {
        return (int) modules.stream().filter(m -> PASSED.equals(m.state())).count();
    }

    public boolean allPassed() {
        return !modules.isEmpty() && passed() == modules.size();
    }

    /** Whether any module is in retraining after a failed quiz (FR-10-04). */
    public boolean retraining() {
        return modules.stream().anyMatch(m -> RETRAINING.equals(m.state()));
    }

    /** Failed quiz attempts across the course: the retraining attempts (FR-10-05). */
    public int failedAttempts() {
        return modules.stream().mapToInt(Module::failedAttempts).sum();
    }

    public int attempts() {
        return modules.stream().mapToInt(m -> m.attempts().size()).sum();
    }

    /** Final competency score: the average of each module's passing score, once all are passed. */
    public Integer finalScore() {
        return allPassed() ? averagePassedScore() : null;
    }

    /** Average passing score of the modules passed so far, or null before the first pass. */
    public Integer averagePassedScore() {
        return modules.stream().filter(m -> m.passedAttempt() != null)
                .mapToInt(m -> m.passedAttempt().getScore()).average()
                .stream().mapToObj(avg -> (int) Math.round(avg)).findFirst().orElse(null);
    }

    public Module of(Long moduleId) {
        return modules.stream().filter(m -> m.module().getId().equals(moduleId)).findFirst()
                .orElseThrow(() -> new BadRequestException("Module not found."));
    }

    /** The next module the learner can study after this one, if any. */
    public Long nextAfter(Long moduleId) {
        boolean after = false;
        for (Module m : modules) {
            if (after && (AVAILABLE.equals(m.state()) || RETRAINING.equals(m.state()))) {
                return m.module().getId();
            }
            after = after || m.module().getId().equals(moduleId);
        }
        return null;
    }
}
