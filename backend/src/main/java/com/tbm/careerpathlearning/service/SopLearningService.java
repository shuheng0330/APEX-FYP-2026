package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.SopFeedbackRequest;
import com.tbm.careerpathlearning.dto.SopLearningCourseDetailDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.dto.SopLearningHomeDto;
import com.tbm.careerpathlearning.dto.SopLearningMaterialDto;
import com.tbm.careerpathlearning.dto.SopLearningQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizResultDto;
import com.tbm.careerpathlearning.dto.SopQuizSubmission;

import java.util.List;
import java.util.UUID;

/**
 * The staff Learning Portal (FR-11): a learner's courses, studying modules in order, taking each
 * module's quiz, retraining after a fail, certification and feedback.
 */
public interface SopLearningService {

    SopLearningHomeDto home(UUID staffId);

    /** Joins an Open course from the portal (FR-11-05). */
    SopLearningCourseDto enrol(Long sopDocumentId, UUID staffId);

    SopLearningCourseDetailDto course(Long sopDocumentId, UUID staffId);

    /** Marks the course started, which starts the learner's own completion window. */
    SopLearningCourseDetailDto start(Long sopDocumentId, UUID staffId);

    SopLearningMaterialDto material(Long moduleId, UUID staffId);

    /** The module's approved quiz questions, without answers. */
    List<SopLearningQuestionDto> quiz(Long moduleId, UUID staffId);

    SopQuizResultDto submit(Long moduleId, SopQuizSubmission submission, UUID staffId);

    SopLearningCourseDetailDto feedback(Long sopDocumentId, SopFeedbackRequest request, UUID staffId);
}
