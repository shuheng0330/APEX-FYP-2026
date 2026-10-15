package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.SopDetailDto;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizReviewEventDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * SOP courses: upload, generation and the material and quiz reviews. Every method except upload takes the
 * {@link SopViewer}, and reads need {@link SopAccess#canView} while changes need {@link SopAccess#canEdit}
 * (FR-07-08, FR-07-19).
 */
public interface SopService {

    // --- Module 1: upload + generation (FR-08) ---
    SopDocumentDto upload(MultipartFile file, String title, String version, String departmentTag, UUID userId);

    /**
     * The courses the viewer may see, newest first.
     *
     * @param mineOnly only the viewer's own courses, as on the upload page
     */
    List<SopDocumentDto> listDocuments(SopViewer viewer, boolean mineOnly);

    SopDetailDto getDetail(Long sopDocumentId, SopViewer viewer);

    SopDocumentDto getStatus(Long sopDocumentId, SopViewer viewer);

    /** Re-run parse + AI generation for an existing document (e.g. after a transient failure). */
    SopDocumentDto regenerateDocument(Long sopDocumentId, SopViewer viewer);

    void deleteDocument(Long sopDocumentId, SopViewer viewer);

    // --- Module 2: trainer review of material (FR-09) ---
    SopModuleDto getModule(Long moduleId, SopViewer viewer);

    SopModuleDto updateModule(Long moduleId, String title, String content, SopViewer viewer);

    SopModuleDto approveModule(Long moduleId, SopViewer viewer);

    SopModuleDto rejectModule(Long moduleId, String reason, SopViewer viewer);

    // --- Module 2: trainer review of quiz (FR-10) ---
    SopQuizQuestionDto updateQuestion(Long questionId, SopQuizQuestionDto dto, SopViewer viewer);

    /** A trainer-written question added to a module's quiz; the quiz needs approving again. */
    SopQuizQuestionDto addQuestion(Long moduleId, SopQuizQuestionDto dto, SopViewer viewer);

    SopModuleDto approveModuleQuiz(Long moduleId, SopViewer viewer);

    SopModuleDto rejectModuleQuiz(Long moduleId, String reason, SopViewer viewer);

    /** Review history of a module's quiz, newest first (FR-08-09). */
    List<SopQuizReviewEventDto> getQuizHistory(Long moduleId, SopViewer viewer);
}
