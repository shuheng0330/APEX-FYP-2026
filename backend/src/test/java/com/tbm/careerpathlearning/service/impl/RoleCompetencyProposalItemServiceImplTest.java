package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalItem;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalItemId;
import com.tbm.careerpathlearning.repository.RoleCompetencyProposalItemRepository;
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
class RoleCompetencyProposalItemServiceImplTest {

    @InjectMocks
    private RoleCompetencyProposalItemServiceImpl service;

    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;
    @Mock private RoleCompetencyProposalItemRepository roleCompetencyProposalItemRepository;
    @Mock private ProposalService proposalService;
    @Mock private StaffService staffService;
    @Mock private RoleService roleService;
    @Mock private CompetencyProposalService competencyProposalService;

    // Common Test Data
    private Long proposalId = 1L;
    private Long roleId = 10L;
    private UUID staffId;
    private ProposalParticipantId competencyProposalId; // The ID of the competency being assigned
    private RoleCompetencyProposalItemId compositeId;
    private RoleCompetencyProposalItemDto dto;
    private RoleCompetencyProposalItem entity;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        // Assume CompetencyProposal is identified by (ProposalId, StaffId) of who proposed it
        competencyProposalId = new ProposalParticipantId(proposalId, UUID.randomUUID());

        compositeId = new RoleCompetencyProposalItemId(proposalId, roleId, staffId, competencyProposalId);

        dto = new RoleCompetencyProposalItemDto();
        dto.setId(compositeId);

        entity = new RoleCompetencyProposalItem();
        entity.setId(compositeId);
    }

    // --- 1. Retrieval Tests ---

    @Test
    void findAllByIdIn_Success() {
        Set<RoleCompetencyProposalItemId> ids = Set.of(compositeId);
        when(roleCompetencyProposalItemRepository.findAllById(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAllByIdIn(ids);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllByProposalStaffId_Success() {
        when(roleCompetencyProposalItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAllByProposalStaffId(proposalId, staffId);

        assertFalse(result.isEmpty());
    }

    @Test
    void findAllByProposalIdIn_Success() {
        Set<Long> pIds = Set.of(proposalId);
        when(roleCompetencyProposalItemRepository.findAllByProposal_IdIn(pIds))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAllByProposalIdIn(pIds);

        assertFalse(result.isEmpty());
    }

    @Test
    void findAllByRoleCompetencyProposalId_Success() {
        when(roleCompetencyProposalItemRepository.findAllByProposal_IdAndRole_IdAndStaff_Id(proposalId, roleId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAllByRoleCompetencyProposalId(proposalId, roleId, staffId);

        assertFalse(result.isEmpty());
    }

    // --- 2. Create All Tests (Complex Validation) ---

    @Test
    void createAll_Success() {
        List<RoleCompetencyProposalItemDto> inputDtos = List.of(dto);

        // A. Mock Dependencies Existence
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));

        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));

        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        CompetencyProposalDto cp = new CompetencyProposalDto(); cp.setId(competencyProposalId);
        when(competencyProposalService.getAllByIdIn(anySet())).thenReturn(List.of(cp));

        // B. Mock Redundancy Check (Return empty = No duplicates)
        when(roleCompetencyProposalItemRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // C. Mock Save
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleCompetencyProposalItemRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.createAll(inputDtos);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalItemRepository).saveAll(anyList());
    }

    @Test
    void createAll_NullOrEmpty_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> service.createAll(null));
        assertThrows(BadRequestException.class, () -> service.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_CompetencyNotFound_ShouldThrowDataAccessException() {
        // Setup valid Proposal, Role, Staff
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        // FAIL: Competency Proposal returns empty list
        when(competencyProposalService.getAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        // Redundancy check pass
        when(roleCompetencyProposalItemRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        List<RoleCompetencyProposalItemDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    @Test
    void createAll_DuplicateRecord_ShouldThrowDataAccessException() {
        // Setup all dependencies exist
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));
        CompetencyProposalDto cp = new CompetencyProposalDto(); cp.setId(competencyProposalId);
        when(competencyProposalService.getAllByIdIn(anySet())).thenReturn(List.of(cp));

        // FAIL: Redundancy check finds existing record
        when(roleCompetencyProposalItemRepository.findAllById(anySet())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto); // Needed for ID extraction in service

        List<RoleCompetencyProposalItemDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    // --- 3. Update Tests ---

    @Test
    void updateAll_Success() {
        List<RoleCompetencyProposalItemDto> dtos = List.of(dto);
        Set<RoleCompetencyProposalItemId> ids = Set.of(compositeId);

        // A. Mock Dependencies
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        CompetencyProposalDto cp = new CompetencyProposalDto(); cp.setId(competencyProposalId);
        when(competencyProposalService.getAllByIdIn(anySet())).thenReturn(List.of(cp));

        // B. Mock Save
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleCompetencyProposalItemRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.updateAll(ids, dtos);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalItemRepository).saveAll(anyList());
    }

    @Test
    void updateAll_InvalidIds_ShouldThrowDataAccessException() {
        List<RoleCompetencyProposalItemDto> dtos = List.of(dto);
        Set<RoleCompetencyProposalItemId> ids = Set.of(compositeId);

        // Fail: Proposal not found
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> service.updateAll(ids, dtos));
    }

    // --- 4. Delete Tests ---

    @Test
    void findAndDeleteAllByProposalId_Success() {
        when(roleCompetencyProposalItemRepository.findAllByProposal_Id(proposalId)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAndDeleteAllByProposalId(proposalId);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalItemRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteAllByProposalStaffId_Success() {
        when(roleCompetencyProposalItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAndDeleteAllByProposalStaffId(proposalId, staffId);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalItemRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteAllByProposalStaffIdIn_Success() {
        Set<Long> pIds = Set.of(proposalId);
        Set<UUID> sIds = Set.of(staffId);

        when(roleCompetencyProposalItemRepository.findAllByProposal_IdInAndStaff_IdIn(pIds, sIds))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalItemDto> result = service.findAndDeleteAllByProposalStaffIdIn(pIds, sIds);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalItemRepository).deleteAll(anyList());
    }

    @Test
    void deleteAllByIdIn_Success() {
        Set<RoleCompetencyProposalItemId> ids = Set.of(compositeId);
        service.deleteAllByIdIn(ids);
        verify(roleCompetencyProposalItemRepository).deleteAllById(ids);
    }
}