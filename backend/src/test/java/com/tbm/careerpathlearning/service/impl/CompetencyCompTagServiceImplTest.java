package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CompTagDto;
import com.tbm.careerpathlearning.dto.CompetencyCompTagDto;
import com.tbm.careerpathlearning.dto.CompetencyDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompetencyCompTag;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import com.tbm.careerpathlearning.repository.CompetencyCompTagRepository;
import com.tbm.careerpathlearning.service.CompTagService;
import com.tbm.careerpathlearning.service.CompetencyService;
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
class CompetencyCompTagServiceImplTest {

    @Mock
    private CompetencyService competencyService;

    @Mock
    private CompTagService compTagService;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @Mock
    private CompetencyCompTagRepository competencyCompTagRepository;

    @InjectMocks
    private CompetencyCompTagServiceImpl competencyCompTagService;

    private CompetencyCompTagDto mockDto;
    private CompetencyCompTag mockEntity;
    private CompetencyCompTagId mockId;
    private Long competencyId = 100L;
    private Long compTagId = 200L;

    @BeforeEach
    void setUp() {
        mockId = new CompetencyCompTagId(competencyId, compTagId);

        mockDto = new CompetencyCompTagDto();
        mockDto.setId(mockId);

        mockEntity = new CompetencyCompTag();
        mockEntity.setId(mockId);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void findAll_ShouldReturnList() {
        when(competencyCompTagRepository.findAll()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagDto> result = competencyCompTagService.findAll();
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIdIn_ShouldReturnList() {
        Set<CompetencyCompTagId> ids = Set.of(mockId);
        when(competencyCompTagRepository.findAllById(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagDto> result = competencyCompTagService.findAllByIdIn(ids);
        assertEquals(1, result.size());
    }

    @Test
    void findAllByCompetencyId_ShouldReturnList() {
        when(competencyCompTagRepository.findAllByCompetency_Id(competencyId)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagDto> result = competencyCompTagService.findAllByCompetencyId(competencyId);
        assertEquals(1, result.size());
    }

    @Test
    void findAllUsedByOther_ShouldReturnList() {
        Set<Long> tagIds = Set.of(compTagId);
        when(competencyCompTagRepository.findAllByCompTag_IdInAndCompetency_IdNot(tagIds, competencyId))
                .thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagDto> result = competencyCompTagService.findAllUsedByOther(tagIds, competencyId);
        assertFalse(result.isEmpty());
    }

    // --- CreateAll Tests (Complex Validation) ---

    @Test
    void createAll_ShouldSave_WhenValidAndNonRedundant() {
        List<CompetencyCompTagDto> input = List.of(mockDto);

        // 1. Mock Competency Existence
        CompetencyDto compDto = new CompetencyDto(); compDto.setId(competencyId);
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(compDto));

        // 2. Mock Tag Existence
        CompTagDto tagDto = new CompTagDto(); tagDto.setId(compTagId);
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(tagDto));

        // 3. Mock Redundancy Check (Return empty = No existing relationship)
        when(competencyCompTagRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        // 4. Mock Saving
        when(appMapper.toEntity(any(CompetencyCompTagDto.class))).thenReturn(mockEntity);
        when(competencyCompTagRepository.saveAll(anyList())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompetencyCompTagDto> result = competencyCompTagService.createAll(input);

        assertEquals(1, result.size());
        verify(competencyCompTagRepository).saveAll(anyList());
    }

    @Test
    void createAll_ShouldThrowException_WhenListEmpty() {
        assertThrows(BadRequestException.class, () -> competencyCompTagService.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ShouldThrowException_WhenCompetencyNotFound() {
        List<CompetencyCompTagDto> input = List.of(mockDto);

        // Competency Service returns empty list (Competency ID 100 not found)
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        // Mock Tag existence to isolate the error
        CompTagDto tagDto = new CompTagDto(); tagDto.setId(compTagId);
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(tagDto));

        // Mock Redundancy check pass
        when(competencyCompTagRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> competencyCompTagService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenCompTagNotFound() {
        List<CompetencyCompTagDto> input = List.of(mockDto);

        // Competency Exists
        CompetencyDto compDto = new CompetencyDto(); compDto.setId(competencyId);
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(compDto));

        // Tag Service returns empty list (Tag ID 200 not found)
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());

        // Mock Redundancy check pass
        when(competencyCompTagRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        assertThrows(DataAccessException.class, () -> competencyCompTagService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenRelationshipRedundant() {
        List<CompetencyCompTagDto> input = List.of(mockDto);

        // Competency Exists
        CompetencyDto compDto = new CompetencyDto(); compDto.setId(competencyId);
        when(competencyService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(compDto));

        // Tag Exists
        CompTagDto tagDto = new CompTagDto(); tagDto.setId(compTagId);
        when(compTagService.findAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(List.of(tagDto));

        // Redundancy Check: Repository returns the EXISTING entity
        when(competencyCompTagRepository.findAllById(anySet())).thenReturn(List.of(mockEntity));
        // Note: The service calls this.findAllByIdIn -> repo.findAllById -> mapper.toDto
        // We need to ensure the Service's internal call returns the DTO with the same ID
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertThrows(DataAccessException.class, () -> competencyCompTagService.createAll(input));
    }

    // --- Delete Tests ---

    @Test
    void deleteByCompetencyIdAndCompTagIdIn_ShouldCallRepo() {
        Set<Long> tagIds = Set.of(compTagId);
        competencyCompTagService.deleteByCompetencyIdAndCompTagIdIn(competencyId, tagIds);

        verify(competencyCompTagRepository).deleteAllByCompetency_IdAndCompTag_IdIn(competencyId, tagIds);
    }

    @Test
    void deleteAllByIdIn_ShouldCallRepo() {
        Set<CompetencyCompTagId> ids = Set.of(mockId);
        competencyCompTagService.deleteAllByIdIn(ids);

        verify(competencyCompTagRepository).deleteAllById(ids);
    }
}