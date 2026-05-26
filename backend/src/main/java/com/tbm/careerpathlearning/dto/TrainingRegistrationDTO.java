package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TrainingRegistrationDTO {

    private Long registrationId;
    private TrainingProgramDTO training;
    private UUID staffId;
    private LocalDateTime registeredAt;

}
