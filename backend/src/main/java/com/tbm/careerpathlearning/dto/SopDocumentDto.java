package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SopDocumentDto {
    private Long id;
    private String title;
    private String version;
    private String originalFilename;
    private String departmentTag;
    private SopGenerationStatus generationStatus;
    private String statusMessage;
    private int moduleCount;
    private OffsetDateTime createdAt;
}
