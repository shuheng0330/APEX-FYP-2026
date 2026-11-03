package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.ReviewStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SopModuleDto {
    private Long id;
    private Long sopDocumentId;
    private int moduleOrder;
    private String title;
    private String content;
    private ReviewStatus reviewStatus;
    private String rejectionReason;
    /** How many questions each attempt at the quiz presents (FR-08-15). */
    private int questionsPerAttempt;
    private List<SopQuizQuestionDto> quiz = new ArrayList<>();
}
