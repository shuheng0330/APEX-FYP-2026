package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.Status;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainingInvitationDTO {

    private Long invitationId;

    @Enumerated(EnumType.STRING)

    private Status status;

    private String reason;

    private LocalDateTime invitedAt;

    private LocalDateTime respondAt;

    private TrainingProgramDTO trainingProgram;

    private StaffDto staff;

    private StaffDto invitedBy;

}
