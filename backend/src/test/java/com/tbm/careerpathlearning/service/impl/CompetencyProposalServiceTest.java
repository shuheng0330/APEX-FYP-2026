package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompetencyProposal;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import com.tbm.careerpathlearning.repository.CompetencyProposalRepository;
import com.tbm.careerpathlearning.service.ProposalService;
import com.tbm.careerpathlearning.service.StaffService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetencyProposalServiceTest {

    @Mock
    private AppMapper appMapper;
    @Mock
    private MessageSource messageSource;
    @Mock
    private ValidationService validationService;
    @Mock
    private CompetencyProposalRepository competencyProposalRepository;
    @Mock
    private ProposalService proposalService;
    @Mock
    private StaffService staffService;

    @InjectMocks
    private CompetencyProposalServiceImpl competencyProposalService;

    private ProposalParticipantId participantId;
    private CompetencyProposal entity;
    private CompetencyProposalDto dto;
    private final UUID STAFF_ID = UUID.randomUUID();
    private final Long PROPOSAL_ID = 1L;

    @BeforeEach
    void setUp() {
        participantId = new ProposalParticipantId(PROPOSAL_ID, STAFF_ID);

        entity = new CompetencyProposal();
        entity.setId(participantId);
        entity.setName("New Competency");

        dto = new CompetencyProposalDto();
        dto.setId(participantId);
        dto.setName("New Competency");
    }

    @Test
    void getById_NotFound_ShouldThrowException() {
        when(competencyProposalRepository.findById(participantId)).thenReturn(Optional.empty());
        when(messageSource.getMessage(any(), any(), any())).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> competencyProposalService.getById(participantId));
    }

    @Test
    void create_Valid_ShouldSaveAll() {
        List<CompetencyProposalDto> dtoList = List.of(dto);
        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setId(PROPOSAL_ID);
        StaffDto staffDto = new StaffDto();
        staffDto.setId(STAFF_ID);

        // Mock existence checks
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(proposalDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staffDto));
        when(competencyProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(competencyProposalRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<CompetencyProposalDto> result = competencyProposalService.createCompetencyProposals(dtoList);

        assertThat(result).hasSize(1);
        verify(competencyProposalRepository).saveAll(anyList());
    }

    @Test
    void create_Redundant_ShouldThrowException() {
        List<CompetencyProposalDto> dtoList = List.of(dto);
        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setId(PROPOSAL_ID);
        StaffDto staffDto = new StaffDto();
        staffDto.setId(STAFF_ID);

        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(proposalDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staffDto));

        // Mock that the record ALREADY exists (redundancy check)
        when(competencyProposalRepository.findAllById(anySet())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        assertThrows(DataAccessException.class, () -> competencyProposalService.createCompetencyProposals(dtoList));
    }

    @Test
    void create_NameTooLong_ShouldThrowBadRequest() {
        dto.setName("A".repeat(256));
        List<CompetencyProposalDto> dtoList = List.of(dto);
        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setId(PROPOSAL_ID);
        StaffDto staffDto = new StaffDto();
        staffDto.setId(STAFF_ID);

        // Mock existence checks
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(proposalDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staffDto));
        when(competencyProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> competencyProposalService.createCompetencyProposals(dtoList));
    }

    @Test
    void create_Description_ShouldThrowBadRequest() {
        dto.setDescription("A".repeat(1001));
        List<CompetencyProposalDto> dtoList = List.of(dto);
        ProposalDto proposalDto = new ProposalDto();
        proposalDto.setId(PROPOSAL_ID);
        StaffDto staffDto = new StaffDto();
        staffDto.setId(STAFF_ID);

        // Mock existence checks
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(proposalDto));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staffDto));
        when(competencyProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> competencyProposalService.createCompetencyProposals(dtoList));
    }

    @Test
    void update_Valid_ShouldUpdate() {
        when(competencyProposalRepository.findById(participantId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(appMapper.toEntity(any(CompetencyProposalDto.class))).thenReturn(entity);
        when(competencyProposalRepository.save(any())).thenReturn(entity);
        when(appMapper.toDto(entity)).thenReturn(dto);

        CompetencyProposalDto result = competencyProposalService.updateCompetencyProposal(participantId, dto);

        assertThat(result).isNotNull();
        verify(competencyProposalRepository).save(any());
    }

    @Test
    void update_BlankName_ShouldThrowBadRequest() {
        when(competencyProposalRepository.findById(participantId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);
        when(validationService.isNullOrBlank(any())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> competencyProposalService.updateCompetencyProposal(participantId, dto));
    }

    @Test
    void update_NameTooLong_ShouldThrowBadRequest() {
        dto.setName("A".repeat(256));
        when(competencyProposalRepository.findById(participantId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> competencyProposalService.updateCompetencyProposal(participantId, dto));
    }

    @Test
    void update_DescriptionTooLong_ShouldThrowBadRequest() {
        dto.setDescription("A".repeat(1001));
        when(competencyProposalRepository.findById(participantId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> competencyProposalService.updateCompetencyProposal(participantId, dto));
    }

    @Test
    void findAndDelete_ShouldReturnDeletedDtos() {
        Set<Long> ids = Set.of(PROPOSAL_ID);
        when(competencyProposalRepository.findAllByProposal_IdIn(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<CompetencyProposalDto> result = competencyProposalService.findAndDeleteAllByProposalIdIn(ids);

        assertThat(result).hasSize(1);
        verify(competencyProposalRepository).deleteAll(anyList());
    }
}