package com.tbm.careerpathlearning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class KpiAssistanceRequest {
    @NotNull @Positive private Long ownerParticipantId;
}
