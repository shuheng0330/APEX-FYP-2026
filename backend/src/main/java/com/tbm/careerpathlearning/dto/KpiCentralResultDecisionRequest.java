package com.tbm.careerpathlearning.dto;

import lombok.Data;

@Data
public class KpiCentralResultDecisionRequest {
    private Long expectedLockVersion;
    private String reason;
}
