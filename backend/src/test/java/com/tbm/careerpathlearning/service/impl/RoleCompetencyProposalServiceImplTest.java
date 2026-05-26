package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleCompetencyProposal;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;
import com.tbm.careerpathlearning.repository.RoleCompetencyProposalRepository;
import com.tbm.careerpathlearning.service.ProposalService;
import com.tbm.careerpathlearning.service.RoleService;
import com.tbm.careerpathlearning.service.StaffService;
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
class RoleCompetencyProposalServiceImplTest {

    @InjectMocks
    private RoleCompetencyProposalServiceImpl service;

    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;
    @Mock private RoleCompetencyProposalRepository roleCompetencyProposalRepository;
    @Mock private ProposalService proposalService;
    @Mock private StaffService staffService;
    @Mock private RoleService roleService;

    // Common Test Data
    private Long proposalId = 1L;
    private Long roleId = 10L;
    private UUID staffId;
    private RoleCompetencyProposalId compositeId;
    private RoleCompetencyProposalDto dto;
    private RoleCompetencyProposal entity;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        compositeId = new RoleCompetencyProposalId(proposalId, roleId, staffId);

        dto = new RoleCompetencyProposalDto();
        dto.setId(compositeId);
        dto.setDescription("Test Description");

        entity = new RoleCompetencyProposal();
        entity.setId(compositeId);
        entity.setDescription("Test Description");
    }

    // --- 1. Get By ID Tests ---

    @Test
    void getById_Success() {
        when(roleCompetencyProposalRepository.findById(compositeId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        RoleCompetencyProposalDto result = service.getById(compositeId);

        assertNotNull(result);
        assertEquals(compositeId, result.getId());
        verify(roleCompetencyProposalRepository).findById(compositeId);
    }

    @Test
    void getById_NotFound_ShouldThrowDataAccessException() {
        when(roleCompetencyProposalRepository.findById(compositeId)).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> service.getById(compositeId));
    }

    // --- 2. Get All / List Tests ---

    @Test
    void getAllByProposalId_Success() {
        when(roleCompetencyProposalRepository.findAllByProposal_Id(proposalId)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> result = service.getAllByProposalId(proposalId);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void getAllByProposalIdIn_Success() {
        Set<Long> ids = Set.of(proposalId);
        when(roleCompetencyProposalRepository.findAllByProposal_IdIn(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> result = service.getAllByProposalIdIn(ids);

        assertFalse(result.isEmpty());
    }

    @Test
    void getAllByIdIn_Success() {
        Set<RoleCompetencyProposalId> ids = Set.of(compositeId);
        when(roleCompetencyProposalRepository.findAllById(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> result = service.getAllByIdIn(ids);

        assertFalse(result.isEmpty());
    }

    // --- 3. Create All Tests (Complex Logic) ---

    @Test
    void createAll_Success() {
        List<RoleCompetencyProposalDto> inputDtos = List.of(dto);

        // A. Mock Dependencies Existence Checks
        ProposalDto proposalDto = new ProposalDto(); proposalDto.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(proposalDto));

        RoleDto roleDto = new RoleDto(); roleDto.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));

        StaffDto staffDto = new StaffDto(); staffDto.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(staffDto));

        // B. Mock Redundancy Check (Return empty list means no duplicates exist in DB)
        // Note: The service calls 'this.getAllByIdIn' which calls repository.findAllById
        // Since 'this.getAllByIdIn' is an internal method call, Mockito spies are tricky.
        // Assuming implementation calls repository directly or we mock repository for the 'existingRoleCompetencyProposalIds' check.
        // Based on your code: `this.getAllByIdIn` calls `repository.findAllById`
        when(roleCompetencyProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // C. Mock Mapping & Saving
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleCompetencyProposalRepository.saveAll(anyList())).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> result = service.createAll(inputDtos);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalRepository).saveAll(any());
    }

    @Test
    void createAll_NullOrEmptyInput_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> service.createAll(null));
        assertThrows(BadRequestException.class, () -> service.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ForeignKeyNotFound_ShouldThrowDataAccessException() {
        // Setup: Return empty lists for dependencies to simulate "Not Found"
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(Collections.emptyList());
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());
        when(staffService.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        // Setup redundancy check mock (empty to pass that check first)
        when(roleCompetencyProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        List<RoleCompetencyProposalDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    @Test
    void createAll_DuplicateRecord_ShouldThrowDataAccessException() {
        // Setup: Dependencies exist
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        // Setup: Record ALREADY exists in DB (Redundancy Check fails)
        when(roleCompetencyProposalRepository.findAllById(anySet())).thenReturn(List.of(entity));
        // Note: 'this.getAllByIdIn' maps entity to DTO, so we need mapper too
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> input = List.of(dto);

        assertThrows(DataAccessException.class, () -> service.createAll(input));
    }

    @Test
    void createAll_DescriptionTooLong_ShouldThrowBadRequest() {
        // Setup: Dependencies exist
        ProposalDto p = new ProposalDto(); p.setId(proposalId);
        when(proposalService.getAllProposalsByIdIn(anySet())).thenReturn(List.of(p));
        RoleDto r = new RoleDto(); r.setId(roleId);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(r));
        StaffDto s = new StaffDto(); s.setId(staffId);
        when(staffService.findAllByIdIn(anySet())).thenReturn(List.of(s));

        // Setup: No redundancy
        when(roleCompetencyProposalRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // Setup: Long Description
        dto.setDescription("A".repeat(1001));
        List<RoleCompetencyProposalDto> input = List.of(dto);

        assertThrows(BadRequestException.class, () -> service.createAll(input));
    }

    // --- 4. Update Tests ---

    @Test
    void update_Success() {
        // Mock getById (must exist first)
        when(roleCompetencyProposalRepository.findById(compositeId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        // Mock Save
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(roleCompetencyProposalRepository.save(entity)).thenReturn(entity);

        RoleCompetencyProposalDto result = service.update(compositeId, dto);

        assertNotNull(result);
        verify(roleCompetencyProposalRepository).save(any());
    }

    @Test
    void update_DescriptionTooLong_ShouldThrowBadRequest() {
        // Mock getById (Must pass first)
        when(roleCompetencyProposalRepository.findById(compositeId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        RoleCompetencyProposalDto updateDto = new RoleCompetencyProposalDto();
        updateDto.setDescription("A".repeat(1001)); // Too long

        assertThrows(BadRequestException.class, () -> service.update(compositeId, updateDto));
    }

    // --- 5. Delete Tests ---

    @Test
    void findAndDeleteById_Success() {
        // Mock getById
        when(roleCompetencyProposalRepository.findById(compositeId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        RoleCompetencyProposalDto result = service.findAndDeleteById(compositeId);

        assertNotNull(result);
        verify(roleCompetencyProposalRepository).deleteById(compositeId);
    }

    @Test
    void findAndDeleteByIdIn_Success() {
        Set<RoleCompetencyProposalId> ids = Set.of(compositeId);

        when(roleCompetencyProposalRepository.findAllById(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> result = service.findAndDeleteByIdIn(ids);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalRepository).deleteAllById(ids);
    }

    @Test
    void findAndDeleteByProposalId_Success() {
        when(roleCompetencyProposalRepository.findAllByProposal_Id(proposalId)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto); // Map result before delete logic returns it

        List<RoleCompetencyProposalDto> result = service.findAndDeleteByProposalId(proposalId);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalRepository).deleteAll(anyList());
    }

    @Test
    void findAndDeleteByProposalIdIn_Success() {
        Set<Long> ids = Set.of(proposalId);
        when(roleCompetencyProposalRepository.findAllByProposal_IdIn(ids)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<RoleCompetencyProposalDto> result = service.findAndDeleteByProposalIdIn(ids);

        assertFalse(result.isEmpty());
        verify(roleCompetencyProposalRepository).deleteAll(anyList());
    }
}