package com.tbm.careerpathlearning.dto;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;
@Data
public class KpiAssessmentEvidenceDto {
    private Long id;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private UUID uploadedBy;
    private OffsetDateTime uploadedAt;
}
