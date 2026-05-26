package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleCompetency;
import com.tbm.careerpathlearning.model.RoleCompetencyId;
import com.tbm.careerpathlearning.repository.RoleCompetencyRepository;
import com.tbm.careerpathlearning.service.CompetencyService;
import com.tbm.careerpathlearning.service.RoleService;
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
class RoleCompetencyServiceTest {

    @Mock
    private RoleService roleService;
    @Mock
    private CompetencyService competencyService;
    @Mock
    private AppMapper appMapper;
    @Mock
    private RoleCompetencyRepository roleCompetencyRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private RoleCompetencyServiceImpl roleCompetencyService;

    private RoleCompetencyId roleCompetencyId;
    private RoleCompetency roleCompetency;
    private RoleCompetencyDto roleCompetencyDto;
    private RoleDto roleDto;
    private CompetencyDto competencyDto;

    @BeforeEach
    void setUp() {
        roleCompetencyId = new RoleCompetencyId(1L, 100L);

        roleDto = new RoleDto();
        roleDto.setId(1L);

        competencyDto = new CompetencyDto();
        competencyDto.setId(100L);

        roleCompetencyDto = new RoleCompetencyDto();
        roleCompetencyDto.setId(roleCompetencyId);

        roleCompetency = new RoleCompetency();
        roleCompetency.setId(roleCompetencyId);
    }

    @Test
    void findAll_ShouldReturnList() {
        when(roleCompetencyRepository.findAll()).thenReturn(List.of(roleCompetency));
        when(appMapper.toDto(roleCompetency)).thenReturn(roleCompetencyDto);

        List<RoleCompetencyDto> result = roleCompetencyService.findAll();

        assertThat(result).hasSize(1);
        verify(roleCompetencyRepository).findAll();
    }

    @Test
    void createAll_NullOrEmpty_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () -> roleCompetencyService.createAll(null));
        assertThrows(BadRequestException.class, () -> roleCompetencyService.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_Valid_ShouldSaveAll() {
        // Arrange
        List<RoleCompetencyDto> dtoList = List.of(roleCompetencyDto);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(competencyDto));
        when(roleCompetencyRepository.findAllByIdIn(anySet())).thenReturn(Collections.emptyList());
        when(appMapper.toEntity(roleCompetencyDto)).thenReturn(roleCompetency);
        when(roleCompetencyRepository.saveAll(any())).thenReturn(List.of(roleCompetency));
        when(appMapper.toDto(roleCompetency)).thenReturn(roleCompetencyDto);

        // Act
        List<RoleCompetencyDto> result = roleCompetencyService.createAll(dtoList);

        // Assert
        assertThat(result).hasSize(1);
        verify(roleCompetencyRepository).saveAll(any());
    }

    @Test
    void createAll_RoleNotFound_ShouldThrowException() {
        List<RoleCompetencyDto> dtoList = List.of(roleCompetencyDto);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> roleCompetencyService.createAll(dtoList));
    }

    @Test
    void createAll_Redundant_ShouldThrowException() {
        List<RoleCompetencyDto> dtoList = List.of(roleCompetencyDto);
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(competencyDto));
        // Mock existing record in DB
        when(roleCompetencyRepository.findAllByIdIn(anySet())).thenReturn(List.of(roleCompetency));
        when(appMapper.toDto(roleCompetency)).thenReturn(roleCompetencyDto);

        assertThrows(DataAccessException.class, () -> roleCompetencyService.createAll(dtoList));
    }

    @Test
    void updateAll_Valid_ShouldReturnUpdatedList() {
        Set<RoleCompetencyId> ids = Set.of(roleCompetencyId);
        List<RoleCompetencyDto> dtoList = List.of(roleCompetencyDto);

        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(roleDto));
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(competencyDto));
        when(appMapper.toEntity(roleCompetencyDto)).thenReturn(roleCompetency);
        when(roleCompetencyRepository.saveAll(any())).thenReturn(List.of(roleCompetency));
        when(appMapper.toDto(roleCompetency)).thenReturn(roleCompetencyDto);

        List<RoleCompetencyDto> result = roleCompetencyService.updateAll(ids, dtoList);

        assertThat(result).hasSize(1);
        verify(roleCompetencyRepository).saveAll(any());
    }

    @Test
    void deleteAllByIdIn_ShouldCallRepo() {
        Set<RoleCompetencyId> ids = Set.of(roleCompetencyId);
        roleCompetencyService.deleteAllByIdIn(ids);
        verify(roleCompetencyRepository).deleteAllByIdIn(ids);
    }

    @Test
    void deleteAllByRoleId_ShouldCallRepo() {
        roleCompetencyService.deleteAllByRoleId(1L);
        verify(roleCompetencyRepository).deleteAllByRoleId(1L);
    }
}