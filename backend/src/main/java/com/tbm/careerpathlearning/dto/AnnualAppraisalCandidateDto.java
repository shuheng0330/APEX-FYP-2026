package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AppraisalStatus;
import java.util.UUID;

public record AnnualAppraisalCandidateDto(Long participantId, UUID employeeId, String employeeName,
        String departmentName, String roleName, Long appraisalId, AppraisalStatus status,
        AnnualAppraisalReadinessDto readiness) {}
