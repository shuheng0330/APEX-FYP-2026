package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import lombok.Data;

import java.util.List;

@Data
public class SopQuizQuestionDto {
    private Long id;
    private Long sopModuleId;
    private QuizQuestionType questionType;
    private String questionText;
    private List<String> options;
    private String correctAnswer;
    private String explanation;
    private ReviewStatus reviewStatus;
    private String rejectionReason;
}
