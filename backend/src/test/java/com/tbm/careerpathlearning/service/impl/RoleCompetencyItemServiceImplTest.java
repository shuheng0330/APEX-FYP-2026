package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleCompetencyItem;
import com.tbm.careerpathlearning.model.RoleCompetencyItemId;
import com.tbm.careerpathlearning.repository.RoleCompetencyItemRepository;
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
class RoleCompetencyItemServiceImplTest {

    @InjectMocks
    private RoleCompetencyItemServiceImpl service;

    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;
    @Mock private RoleCompetencyItemRepository roleCompetencyItemRepository;
    @Mock private ProposalService proposalService;
    @Mock private StaffService staffService;
    @Mock private RoleService roleService;
    @Mock private CompetencyService competencyService;

    // Common Test Data
    private Long proposalId = 1L;
    private Long roleId = 10L;
    private UUID staffId;
    private Long competencyId = 100L;
    private RoleCompetencyItemId compositeId;
    private RoleCompetencyItemDto dto;
    private RoleCompetencyItem entity;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        compositeId = new RoleCompetencyItemId(proposalId, roleId, staffId, competencyId);

        dto = new RoleCompetencyItemDto();
        dto.setId(compositeId);

        entity = new RoleCompetencyItem();
        entity.setId(compositeId);
    }

    // --- 1. Retrieval Tests ---

    @Test
    void findAllByIdIn_Success() {
        Set<RoleCompetencyItemId> ids = Set.of(compositeId);
        when(roleCompetencyItemRepository.findAllById(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.findAllByIdIn(ids);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllByProposalStaffId_Success() {
        when(roleCompetencyItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.findAllByProposalStaffId(proposalId, staffId);

        assertFalse(result.isEmpty());
    }

    @Test
    void findAllByProposalIdIn_Success() {
        Set<Long> ids = Set.of(proposalId);
        when(roleCompetencyItemRepository.findAllByProposal_IdIn(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.findAllByProposalIdIn(ids);

        assertFalse(result.isEmpty());
    }

    // --- 2. Create All Tests (Complex Validation) ---

    @Test
    void createAll_Success() {
        List<RoleCompetencyItemDto> inputDtos = List.of(dto);

        // A. Mock Dependencies Existence
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));

        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));

        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        CompetencyDto c = new CompetencyDto(); c.setId(competencyId);
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(c));

        // B. Mock Redundancy Check (Return empty = No duplicates)
        when(roleCompetencyItemRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // C. Mock Save
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleCompetencyItemRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.createAll(inputDtos);

        assertFalse(result.isEmpty());
        verify(roleCompetencyItemRepository).saveAll(anyList());
    }

    @Test
    void createAll_NullOrEmptyInput_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> service.createAll(null));
        assertThrows(BadRequestException.class, () -> service.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ForeignKeyNotFound_ShouldThrowDataAccessException() {
        // Setup: Proposal OK, Role OK, Staff OK
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        // FAIL: Competency Missing
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        // Redundancy check pass
        when(roleCompetencyItemRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        List<RoleCompetencyItemDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    @Test
    void createAll_DuplicateRecord_ShouldThrowDataAccessException() {
        // Setup: All Dependencies OK
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));
        CompetencyDto c = new CompetencyDto(); c.setId(competencyId);
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(c));

        // FAIL: Redundancy check finds existing record
        when(roleCompetencyItemRepository.findAllById(anySet())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto); // Required for ID check logic

        List<RoleCompetencyItemDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    // --- 3. Update Tests ---

    @Test
    void updateAll_Success() {
        List<RoleCompetencyItemDto> dtos = List.of(dto);
        Set<RoleCompetencyItemId> ids = Set.of(compositeId);

        // A. Mock Dependencies
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        CompetencyDto c = new CompetencyDto(); c.setId(competencyId);
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(c));

        // B. Mock Save
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleCompetencyItemRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.updateAll(ids, dtos);

        assertFalse(result.isEmpty());
        verify(roleCompetencyItemRepository).saveAll(anyList());
    }

    @Test
    void updateAll_InvalidIds_ShouldThrowDataAccessException() {
        List<RoleCompetencyItemDto> dtos = List.of(dto);
        Set<RoleCompetencyItemId> ids = Set.of(compositeId);

        // Fail: Proposal not found
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> service.updateAll(ids, dtos));
    }

    // --- 4. Delete Tests ---

    @Test
    void findAndDeleteAllByProposalStaffId_Success() {
        when(roleCompetencyItemRepository.findAllByProposal_IdAndStaff_Id(proposalId, staffId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.findAndDeleteAllByProposalStaffId(proposalId, staffId);

        assertFalse(result.isEmpty());
        verify(roleCompetencyItemRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteAllByProposalStaffIdIn_Success() {
        Set<Long> pIds = Set.of(proposalId);
        Set<UUID> sIds = Set.of(staffId);

        when(roleCompetencyItemRepository.findAllByProposal_IdInAndStaff_IdIn(pIds, sIds))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.findAndDeleteAllByProposalStaffIdIn(pIds, sIds);

        assertFalse(result.isEmpty());
        verify(roleCompetencyItemRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteAllByProposalId_Success() {
        when(roleCompetencyItemRepository.findAllByProposal_Id(proposalId))
                .thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyItemDto> result = service.findAndDeleteAllByProposalId(proposalId);

        assertFalse(result.isEmpty());
        verify(roleCompetencyItemRepository).deleteAll(anyList());
    }

    @Test
    void deleteAllByIdIn_Success() {
        Set<RoleCompetencyItemId> ids = Set.of(compositeId);
        service.deleteAllByIdIn(ids);
        verify(roleCompetencyItemRepository).deleteAllById(ids);
    }
}