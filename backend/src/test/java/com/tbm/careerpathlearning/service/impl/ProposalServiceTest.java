package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ProposalDto;
import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Proposal;
import com.tbm.careerpathlearning.repository.ProposalRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    @Mock
    private AppMapper appMapper;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ProposalServiceImpl proposalService;

    private Proposal proposal;
    private ProposalDto proposalDto;
    private final Long PROPOSAL_ID = 1L;
    private final UUID USER_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        proposal = new Proposal();
        proposal.setId(PROPOSAL_ID);
        proposal.setType(ProposalType.ROLE_COMPETENCY);
        proposal.setStatus(ProposalStatus.ONGOING);

        proposalDto = new ProposalDto();
        proposalDto.setId(PROPOSAL_ID);
        proposalDto.setType(ProposalType.ROLE_COMPETENCY);
    }

    @Test
    void getProposalById_NotFound_ShouldThrowException() {
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> proposalService.getProposalById(PROPOSAL_ID));
    }

    @Test
    void createProposal_ShouldSetDefaults() {
        // Arrange
        proposalDto.setStatus(null);
        proposalDto.setCreatedAt(null);

        when(appMapper.toEntity(any(ProposalDto.class))).thenReturn(proposal);
        when(proposalRepository.save(any())).thenReturn(proposal);
        when(appMapper.toDto(any(Proposal.class))).thenReturn(proposalDto);

        // Act
        ProposalDto result = proposalService.createProposal(proposalDto);

        // Assert
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.ONGOING);
        assertThat(proposalDto.getCreatedAt()).isNotNull();
        verify(proposalRepository).save(any());
    }

    @Test
    void createProposal_NullType_ShouldThrowBadRequest() {
        proposalDto.setType(null);
        assertThrows(BadRequestException.class, () -> proposalService.createProposal(proposalDto));
    }

    @Test
    void updateProposalStatus_ShouldSuccess() {
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(appMapper.toDto(proposal)).thenReturn(proposalDto);
        when(appMapper.toEntity(any(ProposalDto.class))).thenReturn(proposal);
        when(proposalRepository.save(any())).thenReturn(proposal);
        when(appMapper.toDto(proposal)).thenReturn(proposalDto);

        ProposalDto result = proposalService.updateProposalStatusById(PROPOSAL_ID, ProposalStatus.APPROVED, USER_UUID);

        assertThat(proposalDto.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(proposalDto.getUpdatedBy()).isEqualTo(USER_UUID);
        verify(proposalRepository).save(any());
    }

    @Test
    void updateProposalStatusIn_ShouldSuccess() {
        // Arrange
        Set<Long> ids = Set.of(1L, 2L);
        when(proposalRepository.findAllById(ids)).thenReturn(List.of(proposal));
        when(appMapper.toDto(any(Proposal.class))).thenReturn(proposalDto);
        when(appMapper.toEntity(any(ProposalDto.class))).thenReturn(proposal);
        when(proposalRepository.saveAll(anyList())).thenReturn(List.of(proposal));

        // Act
        List<ProposalDto> results = proposalService.updateProposalStatusByIdIn(ids, ProposalStatus.REJECTED, USER_UUID);

        // Assert
        assertThat(results).isNotEmpty();
        verify(proposalRepository).saveAll(anyList());
    }

    @Test
    void getAllByType_ShouldReturnFilteredList() {
        when(proposalRepository.findAllByType(ProposalType.ROLE_COMPETENCY)).thenReturn(List.of(proposal));
        when(appMapper.toDto(proposal)).thenReturn(proposalDto);

        List<ProposalDto> result = proposalService.getAllByType(ProposalType.ROLE_COMPETENCY);

        assertThat(result).hasSize(1);
        verify(proposalRepository).findAllByType(ProposalType.ROLE_COMPETENCY);
    }
}