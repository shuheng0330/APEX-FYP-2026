package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.AuthorityDto;
import com.tbm.careerpathlearning.dto.TranslatedAuthorityDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.repository.AuthorityRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorityServiceTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private AuthorityRepository authorityRepository;

    @Mock
    private AppMapper appMapper;

    @InjectMocks
    private AuthorityServiceImpl authorityService;

    // Test Data
    private Authority authorityEntity;
    private AuthorityDto authorityDto;
    private final Long AUTHORITY_ID = 1L;
    // Assuming generic enum for test purposes
    private final AuthorityName AUTH_NAME = AuthorityName.values()[0];

    @BeforeEach
    void setUp() {
        // Setup Entity
        authorityEntity = new Authority();
        authorityEntity.setId(AUTHORITY_ID);
        authorityEntity.setName(AUTH_NAME);

        // Setup DTO
        authorityDto = new AuthorityDto();
        authorityDto.setId(AUTHORITY_ID);
        authorityDto.setName(AUTH_NAME);
        authorityDto.setDescriptionKey("desc.key");
        authorityDto.setLabelKey("label.key");
    }

    // --- findAll Tests ---

    @Test
    void findAll_ShouldReturnList() {
        // Arrange
        when(authorityRepository.findAll()).thenReturn(List.of(authorityEntity));
        when(appMapper.toDto(authorityEntity)).thenReturn(authorityDto);

        // Act
        List<AuthorityDto> result = authorityService.findAll();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(authorityDto.getId(), result.get(0).getId());
        verify(authorityRepository).findAll();
    }

    // --- getAllTranslatedAuthorities Tests ---

    @Test
    void getAllTranslatedAuthorities_ShouldReturnTranslatedList() {
        // Arrange
        String expectedLabel = "Translated Label";
        String expectedDesc = "Translated Description";

        // Logic flow: Service calls findAll() -> Repo.findAll() -> Mapper -> Loop -> MessageSource
        when(authorityRepository.findAll()).thenReturn(List.of(authorityEntity));
        when(appMapper.toDto(authorityEntity)).thenReturn(authorityDto);

        // Mock translations
        when(messageSource.getMessage(eq("label.key"), any(), any(Locale.class))).thenReturn(expectedLabel);
        when(messageSource.getMessage(eq("desc.key"), any(), any(Locale.class))).thenReturn(expectedDesc);

        // Act
        List<TranslatedAuthorityDto> result = authorityService.getAllTranslatedAuthorities();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedLabel, result.get(0).getLabel());
        assertEquals(expectedDesc, result.get(0).getDescription());
        assertEquals(authorityDto.getId(), result.get(0).getId());
    }

    // --- findById Tests ---

    @Test
    void findById_WhenExists_ShouldReturnDto() {
        // Arrange
        when(authorityRepository.findById(AUTHORITY_ID)).thenReturn(Optional.of(authorityEntity));
        when(appMapper.toDto(authorityEntity)).thenReturn(authorityDto);

        // Act
        AuthorityDto result = authorityService.findById(AUTHORITY_ID);

        // Assert
        assertNotNull(result);
        assertEquals(AUTHORITY_ID, result.getId());
    }

    @Test
    void findById_WhenNotExists_ShouldThrowException() {
        // Arrange
        when(authorityRepository.findById(AUTHORITY_ID)).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Not Found Error");

        // Act & Assert
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            authorityService.findById(AUTHORITY_ID);
        });

        assertEquals("Not Found Error", exception.getMessage());
    }

    // --- findAllByIdIn Tests ---

    @Test
    void findAllByIdIn_ShouldReturnList() {
        // Arrange
        Set<Long> ids = Set.of(AUTHORITY_ID);
        when(authorityRepository.findAllById(ids)).thenReturn(List.of(authorityEntity));
        when(appMapper.toDto(authorityEntity)).thenReturn(authorityDto);

        // Act
        List<AuthorityDto> result = authorityService.findAllByIdIn(ids);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(authorityRepository).findAllById(ids);
    }

    // --- findByName Tests ---

    @Test
    void findByName_WhenExists_ShouldReturnDto() {
        // Arrange
        when(authorityRepository.findByName(AUTH_NAME)).thenReturn(Optional.of(authorityEntity));
        when(appMapper.toDto(authorityEntity)).thenReturn(authorityDto);

        // Act
        AuthorityDto result = authorityService.findByName(AUTH_NAME);

        // Assert
        assertNotNull(result);
        assertEquals(AUTH_NAME, result.getName());
    }

    @Test
    void findByName_WhenNotExists_ShouldThrowException() {
        // Arrange
        when(authorityRepository.findByName(AUTH_NAME)).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Not Found Error");

        // Act & Assert
        assertThrows(DataAccessException.class, () -> {
            authorityService.findByName(AUTH_NAME);
        });
    }

    // --- findAllByNameIn Tests ---

    @Test
    void findAllByNameIn_ShouldReturnList() {
        // Arrange
        Set<AuthorityName> names = Set.of(AUTH_NAME);
        when(authorityRepository.findAllByNameIn(names)).thenReturn(List.of(authorityEntity));
        when(appMapper.toDto(authorityEntity)).thenReturn(authorityDto);

        // Act
        List<AuthorityDto> result = authorityService.findAllByNameIn(names);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(authorityRepository).findAllByNameIn(names);
    }
}