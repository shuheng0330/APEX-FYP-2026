package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CascaderOptionDTO;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TrainingService {
    List<TrainingProgramDTO> getAllTrainingPrograms();
    TrainingProgramDTO getTrainingProgramById(Long id);
    TrainingProgramDTO createTrainingProgram(TrainingProgramDTO dto, UUID userId);
    TrainingProgramDTO updateTrainingProgram(Long id, TrainingProgramDTO dto, UUID userId);
    List<TrainingProgramDTO> getTrainingProgramsByStaffId(UUID staffId);
    void deleteTrainingProgramById(Long id, UUID userId);
    List<TrainingProgramDTO> getTrainingProgramsByRoleId(Long roleId);
    List<CascaderOptionDTO> getRoleCascaderOptions();
    Page<TrainingProgramDTO> getTrainingPrograms(Pageable pageable);
}
