package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.ProposalParticipant;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import com.tbm.careerpathlearning.repository.ProposalParticipantRepository;
import com.tbm.careerpathlearning.service.ProposalService;
import com.tbm.careerpathlearning.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalParticipantServiceTest {

    @Mock private ProposalParticipantRepository proposalParticipantRepository;
    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;
    @Mock private ProposalService proposalService;
    @Mock private StaffService staffService;

    @InjectMocks
    private ProposalParticipantServiceImpl participantService;

    private ProposalParticipantId participantId;
    private ProposalParticipant participant;
    private ProposalParticipantDto participantDto;
    private UUID staffUuid;
    private Long proposalId = 1L;

    @BeforeEach
    void setUp() {
        staffUuid = UUID.randomUUID();
        participantId = new ProposalParticipantId(proposalId, staffUuid);

        participant = new ProposalParticipant();
        participant.setId(participantId);

        participantDto = new ProposalParticipantDto();
        participantDto.setId(participantId);
    }

    @Test
    void create_NullOrEmpty_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> participantService.createProposalParticipants(null));
        assertThrows(BadRequestException.class, () -> participantService.createProposalParticipants(Collections.emptyList()));
    }

    @Test
    void create_Valid_ShouldSaveAll() {
        List<ProposalParticipantDto> dtoList = List.of(participantDto);

        // Mocking parent entity existence
        ProposalDto pDto = new ProposalDto(); pDto.setId(proposalId);
        StaffDto sDto = new StaffDto(); sDto.setId(staffUuid);

        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(pDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(sDto));
        when(proposalParticipantRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        when(appMapper.toEntity(participantDto)).thenReturn(participant);
        when(proposalParticipantRepository.saveAll(anyList())).thenReturn(List.of(participant));
        when(appMapper.toDto(participant)).thenReturn(participantDto);

        List<ProposalParticipantDto> result = participantService.createProposalParticipants(dtoList);

        assertThat(result).hasSize(1);
        verify(proposalParticipantRepository).saveAll(anyList());
    }

    @Test
    void create_Redundant_ShouldThrowException() {
        List<ProposalParticipantDto> dtoList = List.of(participantDto);

        ProposalDto pDto = new ProposalDto(); pDto.setId(proposalId);
        StaffDto sDto = new StaffDto(); sDto.setId(staffUuid);

        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(pDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(sDto));

        when(proposalParticipantRepository.findAllById(anySet())).thenReturn(List.of(participant));

        when(appMapper.toDto(participant)).thenReturn(participantDto);

        assertThrows(DataAccessException.class, () -> participantService.createProposalParticipants(dtoList));
    }

    @Test
    void create_ProposalNotFound_ShouldThrowException() {
        List<ProposalParticipantDto> dtoList = List.of(participantDto);

        // Mock: Staff exists, but Proposal service returns an empty list for the IDs provided
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(Collections.emptyList());
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(new StaffDto()));

        assertThrows(DataAccessException.class, () -> participantService.createProposalParticipants(dtoList));
    }

    @Test
    void create_StaffNotFound_ShouldThrowException() {
        List<ProposalParticipantDto> dtoList = List.of(participantDto);

        // Mock: Proposal exists, but Staff service returns an empty list
        ProposalDto pDto = new ProposalDto(); pDto.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(pDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> participantService.createProposalParticipants(dtoList));
    }

    @Test
    void findAndDelete_ShouldReturnList() {
        when(proposalParticipantRepository.findAllByProposal_Id(proposalId)).thenReturn(List.of(participant));
        when(appMapper.toDto(participant)).thenReturn(participantDto);

        List<ProposalParticipantDto> result = participantService.findAndDeleteByProposalId(proposalId);

        assertThat(result).hasSize(1);
        verify(proposalParticipantRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteByIdIn_ShouldSucceed() {
        Set<ProposalParticipantId> ids = Set.of(participantId);
        when(proposalParticipantRepository.findAllById(ids)).thenReturn(List.of(participant));
        when(appMapper.toDto(participant)).thenReturn(participantDto);

        List<ProposalParticipantDto> result = participantService.findAndDeleteByIdIn(ids);

        assertThat(result).hasSize(1);
        verify(proposalParticipantRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteById_ShouldSucceed() {
        when(proposalParticipantRepository.getReferenceById(participantId)).thenReturn(participant);
        when(appMapper.toDto(participant)).thenReturn(participantDto);

        ProposalParticipantDto result = participantService.findAndDeleteById(participantId);

        assertThat(result).isNotNull();
        verify(proposalParticipantRepository).delete(participant);
    }

    @Test
    void deleteAllByProposalIdIn_ShouldCallRepo() {
        Set<Long> proposalIds = Set.of(1L, 2L);
        participantService.deleteAllByProposalIdIn(proposalIds);
        verify(proposalParticipantRepository).deleteAllByProposalIdIn(proposalIds);
    }

    @Test
    void deleteAllByProposalId_ShouldCallRepo() {
        participantService.deleteAllByProposalId(proposalId);
        verify(proposalParticipantRepository).deleteAllByProposal_Id(proposalId);
    }
}