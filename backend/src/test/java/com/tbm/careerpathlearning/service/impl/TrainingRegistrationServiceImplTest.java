package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffConflictDTO;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import com.tbm.careerpathlearning.dto.TrainingRegistrationDTO;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingRegistrationServiceImplTest {

    @Mock private TrainingRegistrationRepository registrationRepository;
    @Mock private TrainingProgramRepository trainingProgramRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private TrainingRegistrationServiceImpl registrationService;

    private TrainingRegistrationDTO registrationDto;
    private Staff mockStaff;
    private UUID staffId;
    private TrainingProgram mockTraining;
    private Long trainingId = 100L;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        mockStaff = new Staff();
        mockStaff.setId(staffId);

        mockTraining = new TrainingProgram();
        mockTraining.setTrainingId(trainingId);

        registrationDto = new TrainingRegistrationDTO();
        registrationDto.setStaffId(staffId);
        registrationDto.setTraining(new TrainingProgramDTO());
    }

    @Test
    @DisplayName("Create Registration - Success: Should link staff and set timestamp")
    void createTrainingRegistration_Success() {
        // Arrange
        TrainingRegistration mockEntity = new TrainingRegistration();
        mockEntity.setStaff(mockStaff);
        mockEntity.setTraining(mockTraining);

        // 1. Staff check (Called twice in service: once in main method, once in mapToEntity)
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(mockStaff));

        // 2. Training check
        when(trainingProgramRepository.findById(any())).thenReturn(Optional.of(mockTraining));

        // 3. Conflict check (Return false for success)
        when(registrationRepository.hasConflict(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        when(appMapper.toEntity(any(TrainingProgramDTO.class))).thenReturn(mockTraining);
        when(registrationRepository.save(any(TrainingRegistration.class))).thenReturn(mockEntity);
        when(appMapper.toDto(any(TrainingProgram.class))).thenReturn(registrationDto.getTraining());

        // Act
        TrainingRegistrationDTO result = registrationService.createTrainingRegistration(registrationDto);

        // Assert
        assertNotNull(result);
        verify(registrationRepository).save(any(TrainingRegistration.class));
        // Verify staffRepository was called twice
        verify(staffRepository, times(2)).findById(staffId);
    }

    @Test
    @DisplayName("Get Registered Staff - Should return list of mapped StaffDtos")
    void getRegisteredStaffByTrainingId_ReturnsList() {
        // Arrange
        Long trainingId = 1L;
        when(registrationRepository.getRegisteredStaffByTrainingId(trainingId))
                .thenReturn(List.of(mockStaff));
        when(appMapper.toDto(any(Staff.class))).thenReturn(new StaffDto());

        // Act
        List<StaffDto> result = registrationService.getRegisteredStaffByTrainingId(trainingId);

        // Assert
        assertEquals(1, result.size());
        verify(appMapper).toDto(any(Staff.class));
    }

    @Test
    @DisplayName("Create Registration - Failure: Staff not found")
    void createTrainingRegistration_StaffNotFound_ThrowsException() {
        // Arrange
        when(staffRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                registrationService.createTrainingRegistration(registrationDto)
        );
    }

    @Test
    @DisplayName("Create Registration - Failure: Schedule Conflict")
    void createTrainingRegistration_Conflict_ThrowsBadRequestException() {
        // 1. Ensure the DTO has the ID that matches your stub
        registrationDto.getTraining().setTrainingId(trainingId); // trainingId is 100L

        // 2. Arrange
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(mockStaff));

        // Now this matches because registrationDto.getTraining().getTrainingId() is 100L
        when(trainingProgramRepository.findById(trainingId)).thenReturn(Optional.of(mockTraining));

        when(registrationRepository.hasConflict(any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        when(messageSource.getMessage(any(), any(), any()))
                .thenReturn("Schedule conflict detected");

        // 3. Act & Assert
        assertThrows(BadRequestException.class, () ->
                registrationService.createTrainingRegistration(registrationDto)
        );
    }
    // --- Tests for getRegisteredStaffByTrainingId ---

    @Test
    @DisplayName("Get Registered Staff - Success")
    void getRegisteredStaffByTrainingId_Success() {
        // Arrange
        when(registrationRepository.getRegisteredStaffByTrainingId(trainingId))
                .thenReturn(List.of(mockStaff));
        when(appMapper.toDto(any(Staff.class))).thenReturn(new StaffDto());

        // Act
        List<StaffDto> result = registrationService.getRegisteredStaffByTrainingId(trainingId);

        // Assert
        assertEquals(1, result.size());
        verify(appMapper).toDto(any(Staff.class));
    }


    @Test
    @DisplayName("Get Conflicts - Success")
    void getConflictingRegistrations_Success() {
        // Arrange
        when(trainingProgramRepository.findById(trainingId)).thenReturn(Optional.of(mockTraining));
        when(registrationRepository.findConflictingRegistrations(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new StaffConflictDTO()));

        // Act
        List<StaffConflictDTO> result = registrationService.getConflictingRegistrations(trainingId);

        // Assert
        assertFalse(result.isEmpty());
        verify(registrationRepository).findConflictingRegistrations(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Get Conflicts - Failure: Training Not Found")
    void getConflictingRegistrations_TrainingNotFound() {
        // Arrange
        when(trainingProgramRepository.findById(trainingId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                registrationService.getConflictingRegistrations(trainingId)
        );
        assertTrue(ex.getMessage().contains("Training not found"));
    }
}