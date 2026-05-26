package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.BulkInvitationDTO;
import com.tbm.careerpathlearning.dto.TrainingInvitationDTO;
import com.tbm.careerpathlearning.dto.UpdateInvitationDto;
import com.tbm.careerpathlearning.enums.Status;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.service.TrainingInvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class TrainingInvitationServiceImpl implements TrainingInvitationService {

    @Autowired
    private TrainingInvitationRepository trainingInvitationRepository;

    @Autowired
    private TrainingProgramRepository trainingProgramRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private com.tbm.careerpathlearning.repository.TrainingRegistrationRepository trainingRegistrationRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    MessageSource messageSource;

    private static final String TRAINING_INSUFFICIENT_SLOTS_TITLE = "training.insufficient.slots.title";

    private static final String TRAINING_INSUFFICIENT_SLOTS_MESSAGE = "training.insufficient.slots.message";

    private static final String TRAINING_FULL_TITLE= "training.full.title";

    private static final String TRAINING_FULL_MESSAGE = "training.full.message";

    @Override
    public List<TrainingInvitationDTO> getTrainingInvitations() {
        return trainingInvitationRepository.getActiveTrainingInvitations().stream()
                .map(appMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public List<TrainingInvitationDTO> inviteStaff(BulkInvitationDTO dto, UUID userId) {

        TrainingProgram training = trainingProgramRepository.findById(dto.getTrainingId())
                .orElseThrow(() -> new RuntimeException("Training not found"));

        Staff invitedBy = staffRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("Staff not found"));

        long registeredCount =
                trainingRegistrationRepository.countByTraining_TrainingId(training.getTrainingId());

        int requested = dto.getStaffIds().size();
        int remaining = training.getCapacity() - (int) registeredCount;

        if (remaining <= 0) {
            throw new BadRequestException(
                    messageSource.getMessage(TRAINING_FULL_TITLE, null, Locale.getDefault()),
                    messageSource.getMessage(TRAINING_FULL_MESSAGE, null, Locale.getDefault())
            );
        }

        if (requested > remaining) {
            throw new BadRequestException(
                    messageSource.getMessage(TRAINING_INSUFFICIENT_SLOTS_TITLE, null, Locale.getDefault()),
                    messageSource.getMessage(
                            TRAINING_INSUFFICIENT_SLOTS_MESSAGE,
                            new Object[]{ remaining },
                            Locale.getDefault()
                    )
            );
        }

        List<Staff> staffList = staffRepository.findAllById(dto.getStaffIds());

        List<TrainingInvitation> invitations = staffList.stream()
                .map(staff -> {
                    TrainingInvitation i = new TrainingInvitation();
                    i.setTrainingProgram(training);
                    i.setStaff(staff);
                    i.setStatus(Status.PENDING);
                    i.setInvitedAt(LocalDateTime.now());
                    i.setInvitedBy(invitedBy);
                    return i;
                })
                .collect(Collectors.toList());

        return trainingInvitationRepository.saveAll(invitations).stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<TrainingInvitationDTO> getAllInvitationsByTraining(Long trainingId) {
        TrainingProgram training = trainingProgramRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found with ID: " + trainingId));

        return trainingInvitationRepository.findActiveByTrainingProgram(training).stream()
                .map(invitation -> appMapper.toDto(invitation))
                .collect(Collectors.toList());
    }

    @Override
    public List<TrainingInvitationDTO> getTrainingInvitationsByStaffId(UUID staffId){
        List<TrainingInvitation> invitations = trainingInvitationRepository.findActiveByStaffId(staffId);
        return invitations.stream()
                .map(invitation -> appMapper.toDto(invitation))
                .collect(Collectors.toList());
    }

    @Override
    public TrainingInvitationDTO updateInvitationStatus(Long trainingId, UpdateInvitationDto updateInvitationDto){
        TrainingInvitation trainingInvitation = trainingInvitationRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Invitation not found with ID"));
        trainingInvitation.setStatus(updateInvitationDto.getStatus());
        trainingInvitation.setRespondAt(LocalDateTime.now());
        if (updateInvitationDto.getStatus() == Status.REJECTED){
            trainingInvitation.setReason(updateInvitationDto.getReason());
        }

        TrainingInvitation saved = trainingInvitationRepository.save(trainingInvitation);
        return appMapper.toDto(saved);
    }

}
