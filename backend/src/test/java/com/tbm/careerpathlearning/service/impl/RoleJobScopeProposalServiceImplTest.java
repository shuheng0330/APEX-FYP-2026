package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleJobScopeProposal;
import com.tbm.careerpathlearning.model.RoleJobScopeProposalId;
import com.tbm.careerpathlearning.repository.RoleJobScopeProposalRepository;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleJobScopeProposalServiceImplTest {

    @InjectMocks
    private RoleJobScopeProposalServiceImpl service;

    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;
    @Mock private RoleJobScopeProposalRepository roleJobScopeProposalRepository;
    @Mock private ProposalService proposalService;
    @Mock private StaffService staffService;
    @Mock private RoleService roleService;
    @Mock private JobScopeService jobScopeService;

    // Common Test Data
    private Long proposalId = 1L;
    private Long roleId = 10L;
    private UUID staffId;
    private Long jobScopeId = 100L;
    private RoleJobScopeProposalId compositeId;
    private RoleJobScopeProposalDto dto;
    private RoleJobScopeProposal entity;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        compositeId = new RoleJobScopeProposalId(proposalId, roleId, staffId, jobScopeId);

        dto = new RoleJobScopeProposalDto();
        dto.setId(compositeId);

        entity = new RoleJobScopeProposal();
        entity.setId(compositeId);
    }

    // --- 1. Retrieval Tests ---

    @Test
    void findAllByIdIn_Success() {
        Set<RoleJobScopeProposalId> ids = Set.of(compositeId);
        when(roleJobScopeProposalRepository.findAllById(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.findAllByIdIn(ids);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllByProposalIdIn_Success() {
        Set<Long> ids = Set.of(proposalId);
        when(roleJobScopeProposalRepository.findAllByProposal_IdIn(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.findAllByProposalIdIn(ids);

        assertFalse(result.isEmpty());
    }

    @Test
    void findAllByJobScopeIdIn_Success() {
        Set<Long> ids = Set.of(jobScopeId);
        when(roleJobScopeProposalRepository.findAllByJobScope_IdIn(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.findAllByJobScopeIdIn(ids);

        assertFalse(result.isEmpty());
    }

    @Test
    void getByRoleCompetencyProposalId_Success() {
        when(roleJobScopeProposalRepository.findAllByProposal_IdAndRole_IdAndStaff_Id(proposalId, roleId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.getByRoleCompetencyProposalId(proposalId, roleId, staffId);

        assertFalse(result.isEmpty());
    }

    // --- 2. Create All Tests ---

    @Test
    void createAll_Success() {
        List<RoleJobScopeProposalDto> inputDtos = List.of(dto);

        // A. Mock Dependencies Existence
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));

        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));

        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        JobScopeDto j = new JobScopeDto(); j.setId(jobScopeId);
        when(jobScopeService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(j));

        // B. Mock Redundancy Check (Return empty = No duplicates)
        when(roleJobScopeProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // C. Mock Save
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleJobScopeProposalRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.createAll(inputDtos);

        assertFalse(result.isEmpty());
        verify(roleJobScopeProposalRepository).saveAll(anyList());
    }

    @Test
    void createAll_NullOrEmptyInput_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> service.createAll(null));
        assertThrows(BadRequestException.class, () -> service.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ForeignKeyNotFound_ShouldThrowDataAccessException() {
        // Setup: Missing JobScope (e.g.)
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        // FAIL: JobScope returns empty list
        when(jobScopeService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        // Redundancy check pass
        when(roleJobScopeProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        List<RoleJobScopeProposalDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    @Test
    void createAll_DuplicateRecord_ShouldThrowDataAccessException() {
        // Setup: All dependencies exist
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));
        JobScopeDto j = new JobScopeDto(); j.setId(jobScopeId);
        when(jobScopeService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(j));

        // FAIL: Redundancy Check returns existing record
        when(roleJobScopeProposalRepository.findAllById(anySet())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto); // Required for ID check inside service

        List<RoleJobScopeProposalDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    // --- 3. Delete Tests ---

    @Test
    void findAndDeleteAllByProposalId_Success() {
        when(roleJobScopeProposalRepository.findAllByProposal_Id(proposalId)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.findAndDeleteAllByProposalId(proposalId);

        assertFalse(result.isEmpty());
        verify(roleJobScopeProposalRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteAllByProposalStaffId_Success() {
        when(roleJobScopeProposalRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.findAndDeleteAllByProposalStaffId(proposalId, staffId);

        assertFalse(result.isEmpty());
        verify(roleJobScopeProposalRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteAllByProposalStaffIdIn_Success() {
        Set<Long> pIds = Set.of(proposalId);
        Set<UUID> sIds = Set.of(staffId);

        when(roleJobScopeProposalRepository.findAllByProposal_IdInAndStaff_IdIn(pIds, sIds))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleJobScopeProposalDto> result = service.findAndDeleteAllByProposalStaffIdIn(pIds, sIds);

        assertFalse(result.isEmpty());
        verify(roleJobScopeProposalRepository).deleteAll(anyList());
    }

    @Test
    void deleteAllByIdIn_Success() {
        Set<RoleJobScopeProposalId> ids = Set.of(compositeId);

        service.deleteAllByIdIn(ids);

        verify(roleJobScopeProposalRepository).deleteAllById(ids);
    }
}