package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.CycleStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "evaluation_cycle")
public class EvaluationCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)

    private CycleStatus status; // UPCOMING, OPEN, CLOSED

    private Boolean allDepartments;

    private LocalDateTime createdAt;

    private UUID createdBy;

    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    private LocalDateTime updatedAt;

    private UUID updatedBy;
}
