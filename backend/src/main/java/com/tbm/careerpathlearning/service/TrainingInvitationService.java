package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.BulkInvitationDTO;
import com.tbm.careerpathlearning.dto.TrainingInvitationDTO;
import com.tbm.careerpathlearning.dto.UpdateInvitationDto;

import java.util.List;
import java.util.UUID;

public interface TrainingInvitationService {

    List<TrainingInvitationDTO> getTrainingInvitations();

    List<TrainingInvitationDTO> inviteStaff(BulkInvitationDTO dto, UUID userId);

    List<TrainingInvitationDTO> getAllInvitationsByTraining(Long trainingId);

    List<TrainingInvitationDTO> getTrainingInvitationsByStaffId(UUID staffId);

    TrainingInvitationDTO updateInvitationStatus(Long trainingId, UpdateInvitationDto updateInvitationDto);
}
