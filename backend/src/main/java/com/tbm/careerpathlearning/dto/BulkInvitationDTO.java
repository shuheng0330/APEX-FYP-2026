package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkInvitationDTO {
    private Long trainingId;
    private List<UUID> staffIds; //TODO: Change StaffId into UUID
}
