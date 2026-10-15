package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CompanyKpiResultDelegateDto {
    private Long id;
    private Long reviewPeriodId;
    private UUID staffId;
    private UUID assignedBy;
    private OffsetDateTime assignedAt;
    private UUID revokedBy;
    private OffsetDateTime revokedAt;
}
