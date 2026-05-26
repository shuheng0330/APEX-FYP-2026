package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffConflictDTO;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationDTO;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


@Service
public class TrainingRegistrationServiceImpl implements TrainingRegistrationService {

    @Autowired
    private TrainingProgramRepository trainingRepository;

    @Autowired
    private TrainingRegistrationRepository registrationRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    private static final String TRAINING_REGISTRATION_OVERLAP= "training.registration.overlap";

    @Override
    public TrainingRegistrationDTO createTrainingRegistration(TrainingRegistrationDTO dto) {

        staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff not found"));

        TrainingProgram training = trainingRepository.findById(dto.getTraining().getTrainingId())
                .orElseThrow(() -> new RuntimeException("Training not found"));

        boolean hasConflict = registrationRepository.hasConflict(
                dto.getStaffId(),
                training.getTrainingId(),
                training.getStartDate(),
                training.getEndDate(),
                training.getStartTime(),
                training.getEndTime()
        );

        if (hasConflict) {
            throw new BadRequestException(
                    messageSource.getMessage(TRAINING_REGISTRATION_OVERLAP, null, Locale.getDefault()));
        }

        dto.setRegisteredAt(LocalDateTime.now());
        TrainingRegistration registration = mapToEntity(dto);

        registration.setTraining(training);
        return mapToDTO(registrationRepository.save(registration));
    }

    @Override
    public List<StaffDto> getRegisteredStaffByTrainingId(Long trainingId) {
        List<Staff> registeredStaff = registrationRepository.getRegisteredStaffByTrainingId(trainingId);
        return registeredStaff.stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffConflictDTO> getConflictingRegistrations(Long trainingId) {
        TrainingProgram training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found: " + trainingId));

        return registrationRepository.findConflictingRegistrations(
                trainingId,
                training.getStartDate(),
                training.getEndDate(),
                training.getStartTime(),
                training.getEndTime()
        );
    }

    private TrainingRegistrationDTO mapToDTO(TrainingRegistration trainingRegistration) {
        TrainingRegistrationDTO dto = new TrainingRegistrationDTO();
        dto.setRegistrationId(trainingRegistration.getRegistrationId());
        dto.setRegisteredAt(trainingRegistration.getRegisteredAt());
        dto.setTraining(appMapper.toDto(trainingRegistration.getTraining()));
        dto.setStaffId(trainingRegistration.getStaff().getId());
        return dto;
    }

    private TrainingRegistration mapToEntity(TrainingRegistrationDTO dto) {
        TrainingRegistration trainingRegistration = new TrainingRegistration();
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff not found with ID: " + dto.getStaffId()));

        trainingRegistration.setStaff(staff);
        // We will set Training program in the main method
        trainingRegistration.setTraining(appMapper.toEntity(dto.getTraining())); 
        trainingRegistration.setRegisteredAt(dto.getRegisteredAt());
        return trainingRegistration;
    }

}
