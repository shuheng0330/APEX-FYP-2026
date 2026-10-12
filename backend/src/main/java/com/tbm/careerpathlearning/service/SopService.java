package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.SopDetailDto;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizReviewEventDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface SopService {

    // --- Module 1: upload + generation (FR-08) ---
    SopDocumentDto upload(MultipartFile file, String title, String version, String departmentTag, UUID userId);

    List<SopDocumentDto> listDocuments();

    SopDetailDto getDetail(Long sopDocumentId);

    SopDocumentDto getStatus(Long sopDocumentId);

    /** Re-run parse + AI generation for an existing document (e.g. after a transient failure). */
    SopDocumentDto regenerateDocument(Long sopDocumentId);

    void deleteDocument(Long sopDocumentId);

    // --- Module 2: trainer review of material (FR-09) ---
    SopModuleDto getModule(Long moduleId);

    SopModuleDto updateModule(Long moduleId, String title, String content);

    SopModuleDto approveModule(Long moduleId);

    SopModuleDto rejectModule(Long moduleId, String reason);

    // --- Module 2: trainer review of quiz (FR-10) ---
    SopQuizQuestionDto updateQuestion(Long questionId, SopQuizQuestionDto dto, UUID userId);

    /** A trainer-written question added to a module's quiz; the quiz needs approving again. */
    SopQuizQuestionDto addQuestion(Long moduleId, SopQuizQuestionDto dto, UUID userId);

    SopModuleDto approveModuleQuiz(Long moduleId, UUID userId);

    SopModuleDto rejectModuleQuiz(Long moduleId, String reason, UUID userId);

    /** Review history of a module's quiz, newest first (FR-08-09). */
    List<SopQuizReviewEventDto> getQuizHistory(Long moduleId);
}
