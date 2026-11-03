package com.tbm.careerpathlearning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KpiAssistanceRequest {
    @NotNull @Positive private Long ownerParticipantId;
    @NotBlank @Size(max=10000) private String requestReason;
}
