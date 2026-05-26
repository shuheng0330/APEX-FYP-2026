package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.Status;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class TrainingProgramDTO {

    private Long trainingId;

    private String title;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String venue;

    private Boolean isDeleted;

    private List<OrgChartDto> departments;

    private List<CompetencyDto> competencies;

    private List<Long> departmentIds;

    private List<Long> competencyIds;

    private int capacity;

    private UUID createdBy;

    private Boolean isPublic;

    private LocalDateTime createdAt;

    private UUID updatedBy;

    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private List<String> importantNotes;

    private List<Long> roleIds;

    private Boolean isMandatory;

    private String locationName;

    private Double latitude;

    private Double longitude;

    private Double checkinRadius;

    private int registeredCount;
}

