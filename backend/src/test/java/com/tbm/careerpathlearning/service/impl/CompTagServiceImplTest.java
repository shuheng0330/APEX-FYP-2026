package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CompTagDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompTag;
import com.tbm.careerpathlearning.repository.CompTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompTagServiceImplTest {

    @Mock
    private CompTagRepository compTagRepository;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private CompTagServiceImpl compTagService;

    private CompTag mockEntity;
    private CompTagDto mockDto;
    private final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockEntity = new CompTag();
        mockEntity.setId(1L);
        mockEntity.setTag("Java");
        mockEntity.setDeleted(false);

        mockDto = new CompTagDto();
        mockDto.setId(1L);
        mockDto.setTag("Java");
        mockDto.setDeleted(false);
        mockDto.setCreatedBy(USER_ID);
        mockDto.setUpdatedAt(OffsetDateTime.now());

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void findAllByIsDeletedIsFalse_ShouldReturnList() {
        when(compTagRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompTagDto> result = compTagService.findAllByIsDeletedIsFalse();
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndIdIn_ShouldReturnList() {
        Set<Long> ids = Set.of(1L);
        when(compTagRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompTagDto> result = compTagService.findAllByIsDeletedIsFalseAndIdIn(ids);
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndTagIgnoreCaseIn_ShouldReturnList() {
        Set<String> tags = Set.of("Java");
        when(compTagRepository.findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(anySet())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompTagDto> result = compTagService.findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(tags);
        assertEquals(1, result.size());
    }

    // --- CreateAll Tests (Deduplication Logic) ---

    @Test
    void createAll_ShouldCreateOnlyNewTags() {
        // Scenario: Input has "Java" and "Python". DB already has "Java".
        // Expected: Only "Python" is saved. Result contains both.

        CompTagDto javaDto = new CompTagDto(); javaDto.setTag("Java");
        CompTagDto pythonDto = new CompTagDto(); pythonDto.setTag("Python");
        List<CompTagDto> input = List.of(javaDto, pythonDto);

        // Mock DB finding "Java"
        when(compTagRepository.findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(anySet())).thenReturn(List.of(mockEntity)); // Entity is "Java"
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto); // DTO is "Java"

        // Mock saving "Python"
        CompTag pythonEntity = new CompTag(); pythonEntity.setId(2L); pythonEntity.setTag("Python");
        when(appMapper.toEntity(pythonDto)).thenReturn(pythonEntity);
        when(compTagRepository.saveAll(anyList())).thenReturn(List.of(pythonEntity));

        CompTagDto savedPythonDto = new CompTagDto(); savedPythonDto.setId(2L); savedPythonDto.setTag("Python");
        when(appMapper.toDto(pythonEntity)).thenReturn(savedPythonDto);

        List<CompTagDto> result = compTagService.createAll(input);

        assertEquals(2, result.size()); // Should contain Java (existing) + Python (created)
        verify(compTagRepository).saveAll(argThat(list -> ((List<?>)list).size() == 1)); // Only 1 saved
    }

    @Test
    void createAll_ShouldReturnExisting_WhenAllTagsExist() {
        // Scenario: Input "Java". DB "Java".
        CompTagDto javaDto = new CompTagDto(); javaDto.setTag("Java");

        when(compTagRepository.findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(anySet())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CompTagDto> result = compTagService.createAll(List.of(javaDto));

        assertEquals(1, result.size());
        verify(compTagRepository, never()).saveAll(anyList()); // Nothing new to save
    }

    @Test
    void createAll_ShouldThrowException_WhenTagInvalid() {
        // Scenario: Input tag is null
        CompTagDto invalidDto = new CompTagDto(); invalidDto.setTag(null);
        List<CompTagDto> input = List.of(invalidDto);

        assertThrows(BadRequestException.class, () -> compTagService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenTagTooLong() {
        String longTag = "a".repeat(101);
        CompTagDto invalidDto = new CompTagDto(); invalidDto.setTag(longTag);
        List<CompTagDto> input = List.of(invalidDto);

        assertThrows(BadRequestException.class, () -> compTagService.createAll(input));
    }

    // --- Delete Tests ---

    @Test
    void deleteAllByIdIn_ShouldSoftDelete() {
        Set<Long> ids = Set.of(1L);
        when(compTagRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        when(appMapper.toEntity(any(CompTagDto.class))).thenAnswer(inv -> {
            CompTagDto dto = inv.getArgument(0);
            CompTag c = new CompTag();
            c.setDeleted(dto.getDeleted());
            return c;
        });

        compTagService.deleteAllByIdIn(ids, USER_ID);

        verify(compTagRepository).saveAll(argThat(list -> {
            List<CompTag> entities = (List<CompTag>) list;
            return !entities.isEmpty() && entities.get(0).getDeleted();
        }));
    }
}