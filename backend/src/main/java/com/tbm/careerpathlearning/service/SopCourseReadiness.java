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
 * Whether an SOP course has passed both review gates, so staff may take it: every module's
 * material approved (FR-07-03) and every module's quiz approved (FR-08-08).
 */
@Component
public class SopCourseReadiness {

    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizQuestionRepository sopQuizQuestionRepository;

    public record Readiness(boolean completed, int modules, int approvedModules, int approvedQuizzes) {
        public boolean ready() {
            return completed && modules > 0 && approvedModules == modules && approvedQuizzes == modules;
        }
    }

    public Readiness of(SopDocument doc) {
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(doc.getId());
        Map<Long, List<SopQuizQuestion>> quizzes = quizzesOf(modules);
        int approvedModules = (int) modules.stream().filter(m -> m.getReviewStatus() == ReviewStatus.APPROVED).count();
        int approvedQuizzes = (int) modules.stream().filter(m -> quizApproved(quizzes.get(m.getId()))).count();
        return new Readiness(doc.getGenerationStatus() == SopGenerationStatus.COMPLETED, modules.size(),
                approvedModules, approvedQuizzes);
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
