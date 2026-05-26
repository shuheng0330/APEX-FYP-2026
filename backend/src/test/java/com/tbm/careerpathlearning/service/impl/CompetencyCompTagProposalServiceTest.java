package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompetencyCompTagProposal;
import com.tbm.careerpathlearning.model.CompetencyCompTagProposalId;
import com.tbm.careerpathlearning.repository.CompetencyCompTagProposalRepository;
import com.tbm.careerpathlearning.service.CompTagService;
import com.tbm.careerpathlearning.service.ProposalService;
import com.tbm.careerpathlearning.service.StaffService;
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
class CompetencyCompTagProposalServiceTest {

    @Mock private CompTagService compTagService;
    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;
    @Mock private CompetencyCompTagProposalRepository repository;
    @Mock private ProposalService proposalService;
    @Mock private StaffService staffService;

    @InjectMocks
    private CompetencyCompTagProposalServiceImpl service;

    private CompetencyCompTagProposalId mockId;
    private CompetencyCompTagProposal mockEntity;
    private CompetencyCompTagProposalDto mockDto;

    private final Long PROPOSAL_ID = 1L;
    private final UUID STAFF_ID = UUID.randomUUID();
    private final Long TAG_ID = 10L;

    @BeforeEach
    void setUp() {
        mockId = new CompetencyCompTagProposalId(PROPOSAL_ID, STAFF_ID, TAG_ID);

        mockEntity = new CompetencyCompTagProposal();
        mockEntity.setId(mockId);

        mockDto = new CompetencyCompTagProposalDto();
        mockDto.setId(mockId);
    }

    @Test
    void create_EmptyList_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> service.createCompetencyCompTagProposals(null));
        assertThrows(BadRequestException.class, () -> service.createCompetencyCompTagProposals(Collections.emptyList()));
    }

    @Test
    void create_ProposalNotFound_ShouldThrowException() {
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(Collections.emptyList());
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(new StaffDto()));
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(new CompTagDto()));

        assertThrows(DataAccessException.class, () -> service.createCompetencyCompTagProposals(List.of(mockDto)));
    }

    @Test
    void create_Redundant_ShouldThrowException() {
        // Setup parent mocks to pass existence check
        ProposalDto p = new ProposalDto(); p.setId(PROPOSAL_ID);
        StaffDto s = new StaffDto(); s.setId(STAFF_ID);
        CompTagDto t = new CompTagDto(); t.setId(TAG_ID);

        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(t));

        // Mock that the ID already exists in DB
        when(repository.findAllById(anySet())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertThrows(DataAccessException.class, () -> service.createCompetencyCompTagProposals(List.of(mockDto)));
    }

    @Test
    void create_Valid_ShouldSaveAll() {
        ProposalDto p = new ProposalDto(); p.setId(PROPOSAL_ID);
        StaffDto s = new StaffDto(); s.setId(STAFF_ID);
        CompTagDto t = new CompTagDto(); t.setId(TAG_ID);

        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(t));
        when(repository.findAllById(anySet())).thenReturn(Collections.emptyList());

        when(appMapper.toEntity(any(CompetencyCompTagProposalDto.class))).thenReturn(mockEntity);
        when(repository.saveAll(anyList())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(any(CompetencyCompTagProposal.class))).thenReturn(mockDto);

        List<CompetencyCompTagProposalDto> result = service.createCompetencyCompTagProposals(List.of(mockDto));

        assertThat(result).hasSize(1);
        verify(repository).saveAll(anyList());
    }

    @Test
    void findAndDelete_ShouldReturnDtos() {
        Set<Long> ids = Set.of(PROPOSAL_ID);
        when(repository.findAllByProposal_IdIn(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagProposalDto> result = service.findAndDeleteAllByProposalIdIn(ids);

        assertThat(result).hasSize(1);
        verify(repository).deleteAll(anyList());
    }

    @Test
    void getByProposalStaffId_ShouldReturnList() {
        when(repository.findByProposalIdAndStaffId(PROPOSAL_ID, STAFF_ID)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagProposalDto> result = service.getByProposalStaffId(PROPOSAL_ID, STAFF_ID);

        assertThat(result).hasSize(1);
        verify(repository).findByProposalIdAndStaffId(PROPOSAL_ID, STAFF_ID);
    }
}