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
    private List<SopQuizQuestionDto> quiz = new ArrayList<>();
}
