package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StaffLearningDocumentProgressDto {

    private Long id;

    private Long enrollmentId;

    private Long documentId;

    private Double progress;

    private String lastPosition;

    private LocalDateTime lastAccessedAt;

    private boolean isCompleted;

}
