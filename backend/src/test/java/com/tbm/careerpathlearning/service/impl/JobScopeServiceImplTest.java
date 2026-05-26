package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.JobScopeDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.JobScope;
import com.tbm.careerpathlearning.repository.JobScopeRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobScopeServiceImplTest {

    @Mock
    private JobScopeRepository jobScopeRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private AppMapper appMapper;

    @InjectMocks
    private JobScopeServiceImpl jobScopeService;

    private JobScope mockJobScope;
    private JobScopeDto mockJobScopeDto;
    private UUID userUUID;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();

        mockJobScope = new JobScope();
        mockJobScope.setId(1L);
        mockJobScope.setJobScope("Test Scope");
        mockJobScope.setDeleted(false);

        mockJobScopeDto = new JobScopeDto();
        mockJobScopeDto.setId(1L);
        mockJobScopeDto.setJobScope("Test Scope");
        mockJobScopeDto.setDeleted(false);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void findAllByIsDeletedIsFalse_ShouldReturnList() {
        when(jobScopeRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto);

        List<JobScopeDto> result = jobScopeService.findAllByIsDeletedIsFalse();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndIdIn_ShouldReturnList_WhenAllIdsFound() {
        Set<Long> ids = Set.of(1L);
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto);

        List<JobScopeDto> result = jobScopeService.findAllByIsDeletedIsFalseAndIdIn(ids);

        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndIdIn_ShouldThrowException_WhenIdsMissing() {
        Set<Long> ids = Set.of(1L, 2L); // Requesting 2 IDs
        // Repository only returns 1
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto);

        assertThrows(DataAccessException.class, () -> jobScopeService.findAllByIsDeletedIsFalseAndIdIn(ids));
    }

    @Test
    void findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn_ShouldReturnList() {
        Set<String> scopes = Set.of("Test Scope");
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(anySet())).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto);

        List<JobScopeDto> result = jobScopeService.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(scopes);

        assertFalse(result.isEmpty());
    }

    // --- CreateAll Tests (Complex Logic) ---

    @Test
    void createAll_ShouldReturnExisting_WhenAllExist() {
        // Input: "Test Scope"
        List<JobScopeDto> input = List.of(mockJobScopeDto);

        // Mock DB finding it
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(anySet())).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto); // Used inside the helper method

        // IMPORTANT: The service calls 'findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn' internally.
        // We need to ensure that call returns the list so 'toCreate' becomes empty.

        List<JobScopeDto> result = jobScopeService.createAll(input);

        // Should return the existing one, saveAll should NOT be called
        assertEquals(1, result.size());
        verify(jobScopeRepository, never()).saveAll(anyList());
    }

    @Test
    void createAll_ShouldCreateNew_WhenNoneExist() {
        // Input: "New Scope"
        JobScopeDto newDto = new JobScopeDto();
        newDto.setJobScope("New Scope");

        List<JobScopeDto> input = List.of(newDto);

        // Mock DB finding nothing
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(anySet())).thenReturn(Collections.emptyList());

        // Mock Saving
        when(appMapper.toEntity(any(JobScopeDto.class))).thenReturn(new JobScope());
        when(jobScopeRepository.saveAll(anyList())).thenReturn(List.of(new JobScope()));
        when(appMapper.toDto(any(JobScope.class))).thenReturn(newDto);

        List<JobScopeDto> result = jobScopeService.createAll(input);

        assertEquals(1, result.size());
        verify(jobScopeRepository).saveAll(anyList());
    }

    @Test
    void createAll_ShouldHandleMixed_ExistingAndNew() {
        // Input: "Test Scope" (Exists) and "New Scope" (New)
        JobScopeDto newDto = new JobScopeDto(); newDto.setJobScope("New Scope");
        List<JobScopeDto> input = List.of(mockJobScopeDto, newDto);

        // Mock DB finding "Test Scope"
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(anySet())).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(any(JobScope.class))).thenReturn(newDto);
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto);

        when(appMapper.toEntity(any(JobScopeDto.class))).thenReturn(new JobScope());
        when(jobScopeRepository.saveAll(anyList())).thenReturn(List.of(new JobScope()));

        List<JobScopeDto> result = jobScopeService.createAll(input);

        assertEquals(2, result.size()); // 1 Existing + 1 Created
        verify(jobScopeRepository).saveAll(argThat(list -> ((List<?>)list).size() == 1)); // Only 1 saved
    }

    @Test
    void createAll_ShouldThrowException_WhenScopeIsNull() {
        JobScopeDto invalid = new JobScopeDto();
        invalid.setJobScope(null);
        List<JobScopeDto> input = List.of(invalid);

        assertThrows(BadRequestException.class, () -> jobScopeService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenScopeTooLong() {
        JobScopeDto invalid = new JobScopeDto();
        invalid.setJobScope("A".repeat(1001));
        List<JobScopeDto> input = List.of(invalid);

        assertThrows(BadRequestException.class, () -> jobScopeService.createAll(input));
    }

    // --- Delete Tests ---

    @Test
    void deleteAllByIdIn_ShouldSoftDelete() {
        Set<Long> ids = Set.of(1L);

        // Reuse the logic from 'findAllByIsDeletedIsFalseAndIdIn' test
        when(jobScopeRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockJobScope));
        when(appMapper.toDto(mockJobScope)).thenReturn(mockJobScopeDto);

        when(appMapper.toEntity(any(JobScopeDto.class))).thenAnswer(inv -> {
            JobScopeDto dto = inv.getArgument(0);
            JobScope entity = new JobScope();
            entity.setDeleted(dto.isDeleted());
            return entity;
        });

        jobScopeService.deleteAllByIdIn(ids, userUUID);

        verify(jobScopeRepository).saveAll(argThat(list -> {
            List<JobScope> entities = (List<JobScope>) list;
            return !entities.isEmpty() && entities.get(0).isDeleted();
        }));
    }
}