package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateDocumentProgressDto {

    private UUID staffId;

    private Long documentId;

    private Long materialId;

    private double progress;

    private String lastPosition;

    private Boolean isCompleted;

    private double overallProgress;

}
