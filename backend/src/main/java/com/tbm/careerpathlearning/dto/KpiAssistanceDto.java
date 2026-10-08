package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.time.*;
import java.util.UUID;

@Data
public class KpiAssistanceDto {
    private Long id;
    private Long ownerParticipantId;
    private UUID employeeId;
    private String employeeName;
    private String departmentName;
    private Long reviewPeriodId;
    private String reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private UUID superiorId;
    private String superiorName;
    private KpiAssistanceStatus status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime authorizedAt;
    private UUID authorizedById;
    private String authorizedByName;
    private OffsetDateTime consumedAt;
    private Long planId;
}
