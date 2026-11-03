package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Whether an SOP course has passed both review gates, so staff may be given it: every module's
 * material approved (FR-07-03), every module's quiz approved (FR-08-08), and every quiz at least
 * {@link #MIN_QUIZ_QUESTIONS} questions long (FR-08-14). Staff already taking a course keep taking it.
 */
@Component
public class SopCourseReadiness {

    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;

    /** The fewest questions a quiz may have once approved, and the fewest an attempt presents (FR-08-14, FR-08-15). */
    public static final int MIN_QUIZ_QUESTIONS = 5;

    /** shortQuizzes = the modules whose quiz has fewer than {@link #MIN_QUIZ_QUESTIONS} questions. */
    public record Readiness(boolean completed, int modules, int approvedModules, int approvedQuizzes, int shortQuizzes) {
        public boolean ready() {
            return completed && modules > 0 && approvedModules == modules && approvedQuizzes == modules && shortQuizzes == 0;
        }

        /** Why the course can't be given to staff yet, as a sentence for the trainer; null when it can (FR-09-10). */
        public String blocker() {
            if (!completed || modules == 0) {
                return "Its material hasn't finished generating.";
            }
            if (approvedModules < modules || approvedQuizzes < modules) {
                return "Approve every module's material and quiz first.";
            }
            if (shortQuizzes > 0) {
                return (shortQuizzes == 1 ? "1 quiz has" : shortQuizzes + " quizzes have") + " fewer than " + MIN_QUIZ_QUESTIONS
                        + " questions. Add questions to " + (shortQuizzes == 1 ? "it" : "them")
                        + " first, in a new draft version if staff are taking the course.";
            }
            return null;
        }
    }

    public Readiness of(SopDocument doc) {
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(doc.getId());
        Map<Long, List<SopQuizQuestion>> quizzes = quizzesOf(modules);
        int approvedModules = (int) modules.stream().filter(m -> m.getReviewStatus() == ReviewStatus.APPROVED).count();
        int approvedQuizzes = (int) modules.stream().filter(m -> quizApproved(quizzes.get(m.getId()))).count();
        int shortQuizzes = (int) modules.stream()
                .filter(m -> quizzes.getOrDefault(m.getId(), List.of()).size() < MIN_QUIZ_QUESTIONS).count();
        return new Readiness(doc.getGenerationStatus() == SopGenerationStatus.COMPLETED, modules.size(),
                approvedModules, approvedQuizzes, shortQuizzes);
    }

    /** Each module's quiz questions, by module id. */
    public Map<Long, List<SopQuizQuestion>> quizzesOf(List<SopModule> modules) {
        if (modules.isEmpty()) {
            return Map.of();
        }
        return sopQuizQuestionRepository.findAllBySopModuleIdInOrderByIdAsc(modules.stream().map(SopModule::getId).toList())
                .stream().collect(Collectors.groupingBy(SopQuizQuestion::getSopModuleId));
    }

    /** A quiz staff may take: it has questions and every one of them is approved. */
    public static boolean quizApproved(List<SopQuizQuestion> questions) {
        return questions != null && !questions.isEmpty()
                && questions.stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.APPROVED);
    }
}
