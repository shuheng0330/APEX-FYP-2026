package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class StaffLearningMaterialDto {

    private Long enrollmentId;

    private UUID staffId;

    private Long materialId;

    private LocalDateTime enrolledAt;

    private LocalDateTime completedAt;

    private Double progress;

    private boolean isCompleted = false;

    private UUID enrolledBy;

    private StaffDto staffDto;

//    private String lastPosition; // e.g. video timestamp in seconds, or PDF page number

}
