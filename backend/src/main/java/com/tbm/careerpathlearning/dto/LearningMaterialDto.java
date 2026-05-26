package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class LearningMaterialDto {

    private Long materialId;

    private String title;

    private String description;

    private List<String> learningOutcomes;

    private List<OrgChartDto> departments;

    private List<String> materialType;

    private Double totalDurationAllDoc;

    private List<LearningDocumentDto> learningDocuments;

    private List<CompetencyDto>  competency;

    private UUID createdBy;

    private LocalDateTime createdAt;

    private UUID updatedBy;

    private LocalDateTime updatedAt;
}
