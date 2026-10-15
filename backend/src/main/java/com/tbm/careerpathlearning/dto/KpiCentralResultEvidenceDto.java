package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class KpiCentralResultEvidenceDto {
    private Long id;
    private Long itemId;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private UUID uploadedBy;
    private OffsetDateTime uploadedAt;
}
