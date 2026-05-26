package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CareerPathwayDto;
import com.tbm.careerpathlearning.dto.CareerPathwayRoleDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CareerPathwayRole;
import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import com.tbm.careerpathlearning.repository.CareerPathwayRoleRepository;
import com.tbm.careerpathlearning.service.CareerPathwayService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CareerPathwayRoleServiceImplTest {

    @Mock
    private CareerPathwayRoleRepository careerPathwayRoleRepository;

    @Mock
    private CareerPathwayService careerPathwayService;

    @Mock
    private RoleService roleService;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private CareerPathwayRoleServiceImpl careerPathwayRoleService;

    private CareerPathwayRoleDto mockDto;
    private CareerPathwayRole mockEntity;
    private CareerPathwayRoleId mockId;

    private final Long PATHWAY_ID = 1L;
    private final Long PARENT_ID = 100L;
    private final Long CHILD_ID = 200L;

    @BeforeEach
    void setUp() {
        mockId = new CareerPathwayRoleId(PATHWAY_ID, PARENT_ID, CHILD_ID);

        mockDto = new CareerPathwayRoleDto();
        mockDto.setId(mockId);

        mockEntity = new CareerPathwayRole();
        mockEntity.setId(mockId);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void getAll_ShouldReturnList() {
        when(careerPathwayRoleRepository.findAll()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayRoleDto> result = careerPathwayRoleService.getAll();
        assertEquals(1, result.size());
    }

    @Test
    void getAllByIdIn_ShouldReturnList() {
        Set<CareerPathwayRoleId> ids = Set.of(mockId);
        when(careerPathwayRoleRepository.findAllByIdIn(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayRoleDto> result = careerPathwayRoleService.getAllByIdIn(ids);
        assertEquals(1, result.size());
    }

    @Test
    void getAllByCareerPathwayId_ShouldReturnList() {
        when(careerPathwayRoleRepository.findByCareerPathwayId(PATHWAY_ID)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayRoleDto> result = careerPathwayRoleService.getAllByCareerPathwayId(PATHWAY_ID);
        assertEquals(1, result.size());
    }

    // --- CreateAll Tests (Complex Integrity Checks) ---

    @Test
    void createAll_ShouldSave_WhenAllReferencesExistAndValid() {
        List<CareerPathwayRoleDto> input = List.of(mockDto);

        // 1. Mock Career Pathway Exists
        CareerPathwayDto pathDto = new CareerPathwayDto(); pathDto.setId(PATHWAY_ID);
        when(careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(pathDto));

        // 2. Mock Roles Exist (Parent and Child)
        RoleDto parentRole = new RoleDto(); parentRole.setId(PARENT_ID);
        RoleDto childRole = new RoleDto(); childRole.setId(CHILD_ID);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(parentRole, childRole));

        // 3. Mock Not Redundant
        when(careerPathwayRoleRepository.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        // 4. Mock Saving
        when(appMapper.toEntity(any(CareerPathwayRoleDto.class))).thenReturn(mockEntity);
        when(careerPathwayRoleRepository.saveAll(anyList())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayRoleDto> result = careerPathwayRoleService.createAll(input);

        assertEquals(1, result.size());
        verify(careerPathwayRoleRepository).saveAll(anyList());
    }

    @Test
    void createAll_ShouldThrowException_WhenListEmpty() {
        assertThrows(BadRequestException.class, () -> careerPathwayRoleService.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ShouldThrowException_WhenCareerPathwayNotFound() {
        List<CareerPathwayRoleDto> input = List.of(mockDto);

        // Pathway service returns empty
        when(careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        // Roles exist
        RoleDto parentRole = new RoleDto(); parentRole.setId(PARENT_ID);
        RoleDto childRole = new RoleDto(); childRole.setId(CHILD_ID);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(parentRole, childRole));

        // Redundancy check mock
        when(careerPathwayRoleRepository.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> careerPathwayRoleService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenRoleNotFound() {
        List<CareerPathwayRoleDto> input = List.of(mockDto);

        // Pathway exists
        CareerPathwayDto pathDto = new CareerPathwayDto(); pathDto.setId(PATHWAY_ID);
        when(careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(pathDto));

        // Roles missing (e.g., Parent found but Child missing)
        RoleDto parentRole = new RoleDto(); parentRole.setId(PARENT_ID);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(parentRole)); // Only 1 returned

        // Redundancy check mock
        when(careerPathwayRoleRepository.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> careerPathwayRoleService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenRedundantRelationship() {
        List<CareerPathwayRoleDto> input = List.of(mockDto);

        // Dependencies exist
        CareerPathwayDto pathDto = new CareerPathwayDto(); pathDto.setId(PATHWAY_ID);
        when(careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(pathDto));

        RoleDto parentRole = new RoleDto(); parentRole.setId(PARENT_ID);
        RoleDto childRole = new RoleDto(); childRole.setId(CHILD_ID);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(parentRole, childRole));

        // Redundancy check: Relationship ALREADY exists
        when(careerPathwayRoleRepository.findAllByIdIn(anySet())).thenReturn(List.of(mockEntity));
        // Note: The service calls getAllByIdIn -> findAllByIdIn -> toDto
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertThrows(DataAccessException.class, () -> careerPathwayRoleService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenRecursiveReference() {
        // Scenario: Parent ID same as Child ID
        CareerPathwayRoleId recursiveId = new CareerPathwayRoleId(PATHWAY_ID, PARENT_ID, PARENT_ID);
        CareerPathwayRoleDto recursiveDto = new CareerPathwayRoleDto();
        recursiveDto.setId(recursiveId);
        List<CareerPathwayRoleDto> input = List.of(recursiveDto);

        // Dependencies exist
        CareerPathwayDto pathDto = new CareerPathwayDto(); pathDto.setId(PATHWAY_ID);
        when(careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(pathDto));

        RoleDto parentRole = new RoleDto(); parentRole.setId(PARENT_ID);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(parentRole));

        // Redundancy check mock
        when(careerPathwayRoleRepository.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> careerPathwayRoleService.createAll(input));
    }

    // --- Delete Tests ---

    @Test
    void deleteAllByIdIn_ShouldCallRepo() {
        Set<CareerPathwayRoleId> ids = Set.of(mockId);
        careerPathwayRoleService.deleteAllByIdIn(ids);
        verify(careerPathwayRoleRepository).deleteAllById(ids);
    }

    @Test
    void deleteByCareerPathwayId_ShouldCallRepo() {
        careerPathwayRoleService.deleteByCareerPathwayId(PATHWAY_ID);
        verify(careerPathwayRoleRepository).deleteAllByCareerPathwayId(PATHWAY_ID);
    }

    @Test
    void deleteAllByCareerPathwayIdIn_ShouldCallRepo() {
        Set<Long> ids = Set.of(PATHWAY_ID);
        careerPathwayRoleService.deleteAllByCareerPathwayIdIn(ids);
        verify(careerPathwayRoleRepository).deleteAllByCareerPathway_IdIn(ids);
    }
}