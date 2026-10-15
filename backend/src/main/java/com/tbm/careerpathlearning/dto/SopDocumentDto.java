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

    /** Preparer shown on the material (FR-07-07); the course's owner. */
    private String preparedByName;

    /** Whether the signed-in person may change this course: only its owner can (FR-07-08, FR-07-19). */
    private boolean editable;
}
