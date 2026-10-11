package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.KpiVersionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
public record KpiRevisionDecisionDto(String action,UUID actor,OffsetDateTime at,KpiVersionStatus from,KpiVersionStatus to,String reason) {}
