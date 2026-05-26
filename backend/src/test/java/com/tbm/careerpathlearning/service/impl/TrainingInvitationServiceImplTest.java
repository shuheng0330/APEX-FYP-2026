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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingInvitationServiceImplTest {

    @Mock private TrainingInvitationRepository trainingInvitationRepository;
    @Mock private TrainingProgramRepository trainingProgramRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private TrainingRegistrationRepository trainingRegistrationRepository;
    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private TrainingInvitationServiceImpl invitationService;

    private TrainingProgram mockTraining;
    private Staff mockAdmin;
    private BulkInvitationDTO bulkDto;
    private UUID adminId;
    private TrainingInvitation invitation;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        mockAdmin = new Staff();
        mockAdmin.setId(adminId);

        mockTraining = new TrainingProgram();
        mockTraining.setTrainingId(1L);
        mockTraining.setCapacity(10);

        invitation = new TrainingInvitation();
        invitation.setInvitationId(100L);

        bulkDto = new BulkInvitationDTO();
        bulkDto.setTrainingId(1L);
        bulkDto.setStaffIds(List.of(UUID.randomUUID(), UUID.randomUUID())); // Requesting 2 slots
    }


    @Test
    @DisplayName("Invite Staff - Failure: Training is already full")
    void inviteStaff_TrainingFull_ThrowsBadRequestException() {
        // Arrange: 10 capacity, 10 already registered
        when(trainingProgramRepository.findById(1L)).thenReturn(Optional.of(mockTraining));
        when(staffRepository.findByIdAndIsDeletedFalse(adminId)).thenReturn(Optional.of(mockAdmin));
        when(trainingRegistrationRepository.countByTraining_TrainingId(1L)).thenReturn(10L);

        when(messageSource.getMessage(eq("training.full.title"), any(), any())).thenReturn("Full");
        when(messageSource.getMessage(eq("training.full.message"), any(), any())).thenReturn("Training is full");

        // Act & Assert
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                invitationService.inviteStaff(bulkDto, adminId)
        );
        assertEquals("Training is full", ex.getMessage());
    }

    @Test
    @DisplayName("Invite Staff - Failure: Insufficient slots for requested staff count")
    void inviteStaff_InsufficientSlots_ThrowsBadRequestException() {
        // Arrange: 10 capacity, 9 registered. Only 1 slot left, but bulkDto requests 2.
        when(trainingProgramRepository.findById(1L)).thenReturn(Optional.of(mockTraining));
        when(staffRepository.findByIdAndIsDeletedFalse(adminId)).thenReturn(Optional.of(mockAdmin));
        when(trainingRegistrationRepository.countByTraining_TrainingId(1L)).thenReturn(9L);

        when(messageSource.getMessage(eq("training.insufficient.slots.title"), any(), any())).thenReturn("Limit reached");

        // Act & Assert
        assertThrows(BadRequestException.class, () -> invitationService.inviteStaff(bulkDto, adminId));
        verify(trainingInvitationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Invite Staff - Success: Creates invitations when slots are available")
    void inviteStaff_Success() {
        // Arrange: 10 capacity, 5 registered (5 slots left). Requesting 2.
        when(trainingProgramRepository.findById(1L)).thenReturn(Optional.of(mockTraining));
        when(staffRepository.findByIdAndIsDeletedFalse(adminId)).thenReturn(Optional.of(mockAdmin));
        when(trainingRegistrationRepository.countByTraining_TrainingId(1L)).thenReturn(5L);

        List<Staff> staffToInvite = List.of(new Staff(), new Staff());
        when(staffRepository.findAllById(any())).thenReturn(staffToInvite);
        when(trainingInvitationRepository.saveAll(any())).thenReturn(List.of(new TrainingInvitation(), new TrainingInvitation()));

        // Act
        List<TrainingInvitationDTO> result = invitationService.inviteStaff(bulkDto, adminId);

        // Assert
        assertNotNull(result);
        verify(trainingInvitationRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("getTrainingInvitations - Should return list")
    void getTrainingInvitations_Success() {
        // Targets 0% method: getTrainingInvitations
        when(trainingInvitationRepository.getActiveTrainingInvitations()).thenReturn(List.of(invitation));
        when(appMapper.toDto(any(TrainingInvitation.class))).thenReturn(new TrainingInvitationDTO());

        List<TrainingInvitationDTO> result = invitationService.getTrainingInvitations();

        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("inviteStaff - Should throw BadRequestException when training is full")
    void inviteStaff_TrainingFull() {
        // Targets branch coverage in inviteStaff
        BulkInvitationDTO dto = new BulkInvitationDTO();
        dto.setTrainingId(1L);
        dto.setStaffIds(List.of(UUID.randomUUID()));

        when(trainingProgramRepository.findById(1L)).thenReturn(Optional.of(mockTraining));
        when(staffRepository.findByIdAndIsDeletedFalse(adminId)).thenReturn(Optional.of(mockAdmin));
        // Capacity 10, Registered 10 -> Remaining 0
        when(trainingRegistrationRepository.countByTraining_TrainingId(1L)).thenReturn(10L);
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Error");

        assertThrows(BadRequestException.class, () -> invitationService.inviteStaff(dto, adminId));
    }
    @Test
    @DisplayName("Update Status - Accepted (Branch Coverage for no reason)")
    void updateInvitationStatus_Accepted() {
        UpdateInvitationDto updateDto = new UpdateInvitationDto();
        updateDto.setStatus(Status.ACCEPTED); // No reason field usually needed here

        when(trainingInvitationRepository.findById(100L)).thenReturn(Optional.of(invitation));
        when(trainingInvitationRepository.save(any())).thenReturn(invitation);

        invitationService.updateInvitationStatus(100L, updateDto);

        assertEquals(Status.ACCEPTED, invitation.getStatus());
        assertNull(invitation.getReason()); // Ensures reason isn't set when not REJECTED
    }

    @Test
    @DisplayName("updateInvitationStatus - Should handle REJECTED status with reason")
    void updateInvitationStatus_Rejected() {
        // Targets 0% method and branch coverage for status == REJECTED
        UpdateInvitationDto updateDto = new UpdateInvitationDto();
        updateDto.setStatus(Status.REJECTED);
        updateDto.setReason("Not available");

        when(trainingInvitationRepository.findById(100L)).thenReturn(Optional.of(invitation));
        when(trainingInvitationRepository.save(any())).thenReturn(invitation);
        when(appMapper.toDto(any(TrainingInvitation.class))).thenReturn(new TrainingInvitationDTO());

        invitationService.updateInvitationStatus(100L, updateDto);

        assertEquals(Status.REJECTED, invitation.getStatus());
        assertEquals("Not available", invitation.getReason());
    }

    @Test
    @DisplayName("getTrainingInvitationsByStaffId - Should return staff specific list")
    void getTrainingInvitationsByStaffId_Success() {
        // Targets 0% method: getTrainingInvitationsByStaffId
        when(trainingInvitationRepository.findActiveByStaffId(adminId)).thenReturn(List.of(invitation));
        when(appMapper.toDto(any(TrainingInvitation.class))).thenReturn(new TrainingInvitationDTO());

        List<TrainingInvitationDTO> result = invitationService.getTrainingInvitationsByStaffId(adminId);

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
