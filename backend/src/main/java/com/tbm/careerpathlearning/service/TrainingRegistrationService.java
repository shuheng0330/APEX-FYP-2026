package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffConflictDTO;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationDTO;

import java.util.List;

public interface TrainingRegistrationService {
    TrainingRegistrationDTO createTrainingRegistration(TrainingRegistrationDTO dto);
    List<StaffDto> getRegisteredStaffByTrainingId(Long trainingId);
    List<StaffConflictDTO> getConflictingRegistrations(Long trainingId);
}
