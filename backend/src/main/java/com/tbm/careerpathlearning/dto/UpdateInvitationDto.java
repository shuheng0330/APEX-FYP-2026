package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.Status;
import lombok.Data;

@Data
public class UpdateInvitationDto {
    private Status status;
    private String reason;
}
