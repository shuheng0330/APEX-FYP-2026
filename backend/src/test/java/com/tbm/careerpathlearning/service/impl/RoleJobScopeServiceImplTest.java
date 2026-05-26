package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.JobScopeDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.dto.RoleJobScopeDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleJobScope;
import com.tbm.careerpathlearning.model.RoleJobScopeId;
import com.tbm.careerpathlearning.repository.RoleJobScopeRepository;
import com.tbm.careerpathlearning.service.JobScopeService;
import com.tbm.careerpathlearning.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleJobScopeServiceImplTest {

    @Mock private RoleService roleService;
    @Mock private JobScopeService jobScopeService;
    @Mock private RoleJobScopeRepository roleJobScopeRepository;
    @Mock private AppMapper appMapper;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private RoleJobScopeServiceImpl roleJobScopeService;

    private RoleJobScope mockEntity;
    private RoleJobScopeDto mockDto;
    private RoleJobScopeId mockId;
    private Long roleId = 100L;
    private Long jobScopeId = 200L;

    @BeforeEach
    void setUp() {
        mockId = new RoleJobScopeId(roleId, jobScopeId);

        mockEntity = new RoleJobScope();
        mockEntity.setId(mockId);

        mockDto = new RoleJobScopeDto();
        mockDto.setId(mockId);

        // Lenient stubs for common message source calls
        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void findAll_ShouldReturnList() {
        when(roleJobScopeRepository.findAll()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertFalse(roleJobScopeService.findAll().isEmpty());
    }

    @Test
    void findAllByIdIn_ShouldReturnList() {
        Set<RoleJobScopeId> ids = Set.of(mockId);
        when(roleJobScopeRepository.findAllById(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertFalse(roleJobScopeService.findAllByIdIn(ids).isEmpty());
    }

    @Test
    void findAllByRoleId_ShouldReturnList() {
        when(roleJobScopeRepository.findAllByRole_Id(roleId)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertFalse(roleJobScopeService.findAllByRoleId(roleId).isEmpty());
    }

    // --- CreateAll Tests (Complex Integrity Logic) ---

    @Test
    void createAll_ShouldSave_WhenValid() {
        List<RoleJobScopeDto> dtos = List.of(mockDto);
        RoleDto mockRoleDto = new RoleDto(); mockRoleDto.setId(roleId);
        JobScopeDto mockJobScopeDto = new JobScopeDto(); mockJobScopeDto.setId(jobScopeId);

        // 1. Mock Role Check (Exists)
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockRoleDto));

        // 2. Mock JobScope Check (Exists)
        when(jobScopeService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockJobScopeDto));

        // 3. Mock Redundancy Check (Existing Mapping does NOT exist)
        when(roleJobScopeRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // 4. Mock Saving
        when(appMapper.toEntity(any(RoleJobScopeDto.class))).thenReturn(mockEntity);
        when(roleJobScopeRepository.saveAll(anyList())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(any(RoleJobScope.class))).thenReturn(mockDto);

        List<RoleJobScopeDto> result = roleJobScopeService.createAll(dtos);

        assertFalse(result.isEmpty());
        verify(roleJobScopeRepository).saveAll(anyList());
    }

    @Test
    void createAll_ShouldThrowException_WhenListEmpty() {
        assertThrows(BadRequestException.class, () -> roleJobScopeService.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ShouldThrowException_WhenRoleNotFound() {
        List<RoleJobScopeDto> dtos = List.of(mockDto);

        // Role Service returns EMPTY list (Role not found)
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> roleJobScopeService.createAll(dtos));
    }

    @Test
    void createAll_ShouldThrowException_WhenJobScopeNotFound() {
        List<RoleJobScopeDto> dtos = List.of(mockDto);
        RoleDto mockRoleDto = new RoleDto(); mockRoleDto.setId(roleId);

        // Role found
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockRoleDto));
        // JobScope NOT found
        when(jobScopeService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> roleJobScopeService.createAll(dtos));
    }

    @Test
    void createAll_ShouldThrowException_WhenRelationshipRedundant() {
        List<RoleJobScopeDto> dtos = List.of(mockDto);
        RoleDto mockRoleDto = new RoleDto(); mockRoleDto.setId(roleId);
        JobScopeDto mockJobScopeDto = new JobScopeDto(); mockJobScopeDto.setId(jobScopeId);

        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockRoleDto));
        when(jobScopeService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(mockJobScopeDto));

        when(roleJobScopeRepository.findAllById(anySet())).thenReturn(List.of(mockEntity));

        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);
        // ---------------------------------------------------------------------------------

        assertThrows(DataAccessException.class, () -> roleJobScopeService.createAll(dtos));
    }

    // --- Delete Tests ---

    @Test
    void deleteByRoleIdAndJobScopeIdIn_ShouldCallDelete() {
        roleJobScopeService.deleteByRoleIdAndJobScopeIdIn(roleId, Set.of(jobScopeId));
        verify(roleJobScopeRepository).deleteAllByRole_IdAndJobScope_IdIn(anyLong(), anySet());
    }

    @Test
    void deleteAllByIdIn_ShouldCallDelete() {
        roleJobScopeService.deleteAllByIdIn(Set.of(mockId));
        verify(roleJobScopeRepository).deleteAllById(anySet());
    }

    @Test
    void findAndDeleteAllByRoleId_ShouldReturnDeletedItems() {
        when(roleJobScopeRepository.findAllByRole_Id(roleId)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<RoleJobScopeDto> result = roleJobScopeService.findAndDeleteAllByRoleId(roleId);

        assertFalse(result.isEmpty());
        verify(roleJobScopeRepository).deleteAll(anyList());
    }

    // --- Complex Query Tests ---

    @Test
    void findJobScopesUsedByOtherRoles_ShouldReturnList() {
        when(roleJobScopeRepository.findAllByJobScope_IdInAndRole_IdNot(anySet(), anyLong())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertFalse(roleJobScopeService.findJobScopesUsedByOtherRoles(Set.of(jobScopeId), roleId).isEmpty());
    }

    @Test
    void findJobScopesUsedByRoleIdNotIn_ShouldReturnList() {
        when(roleJobScopeRepository.findAllByJobScope_IdInAndRole_IdNotIn(anySet(), anySet())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertFalse(roleJobScopeService.findJobScopesUsedByRoleIdNotIn(Set.of(jobScopeId), Set.of(roleId)).isEmpty());
    }
}