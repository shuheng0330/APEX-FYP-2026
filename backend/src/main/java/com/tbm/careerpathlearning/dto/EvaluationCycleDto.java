package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.CycleStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EvaluationCycleDto {

    private Long id;

    private LocalDate startDate;

    private LocalDate endDate;

    private CycleStatus status; // UPCOMING, OPEN, CLOSED

    private Boolean allDepartments;

    private LocalDateTime createdAt;

    private UUID createdBy;

    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    private LocalDateTime updatedAt;

    private UUID updatedBy;
}
