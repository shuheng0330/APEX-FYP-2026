package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class KpiAssistanceEmployeeDto {
    private Long ownerParticipantId;
    private UUID employeeId;
    private String employeeName;
    private String departmentName;
    private Long reviewPeriodId;
    private String reviewPeriodName;
}
