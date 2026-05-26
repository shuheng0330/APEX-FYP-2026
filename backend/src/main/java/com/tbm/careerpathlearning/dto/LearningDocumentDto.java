package com.tbm.careerpathlearning.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor  // Necessary for Jackson/JSON mapping
@AllArgsConstructor
public class LearningDocumentDto {

    private Long documentId;

    private String title;

    private String fileUrl;

    private String signedUrl;

    private Integer totalPages;

    private String fileType;

    private Double totalDuration;

    private LocalDateTime createdAt;

    private LearningMaterialDto learningMaterialDto;

}
