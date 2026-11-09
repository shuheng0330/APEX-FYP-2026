package com.tbm.careerpathlearning.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record AttitudeAssignmentRequest(@NotNull UUID requestId,
        @NotNull @Positive Long expectedConfigurationId,@NotNull @Positive Long configurationId,
        @NotBlank @Size(max=1000) String reason) {}
