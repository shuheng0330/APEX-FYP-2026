package com.tbm.careerpathlearning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class LearningMaterialRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    private List<String> learningOutcomes;

    @NotEmpty(message = "At least one department must be selected")
    private List<Long> departmentIds;

    @NotEmpty(message = "Please select at least one material type")
    private List<String> materialType;

    @NotEmpty(message = "At least one learning document is required")
    private List<LearningDocumentDto> learningDocuments;

    @NotEmpty(message = "At least one competency must be assigned")
    private List<Long> competencyIds;

}
