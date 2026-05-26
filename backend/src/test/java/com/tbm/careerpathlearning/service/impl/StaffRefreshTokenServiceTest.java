package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffRefreshTokenDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.StaffRefreshToken;
import com.tbm.careerpathlearning.repository.StaffRefreshTokenRepository;
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
class StaffRefreshTokenServiceTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private AppMapper appMapper;

    @Mock
    private StaffRefreshTokenRepository staffRefreshTokenRepository;

    @InjectMocks
    private StaffRefreshTokenServiceImpl staffRefreshTokenService;

    private UUID staffId;
    private StaffRefreshToken entity;
    private StaffRefreshTokenDto dto;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();

        entity = new StaffRefreshToken();
        entity.setStaffId(staffId);
        entity.setToken("old-token");

        dto = new StaffRefreshTokenDto();
        dto.setStaffId(staffId);
        dto.setToken("new-token");
        dto.setExpiresAt(OffsetDateTime.now().plusDays(7));
    }

    @Test
    void getAllStaffRefreshTokens_ShouldReturnList() {
        when(staffRefreshTokenRepository.findAll()).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        List<StaffRefreshTokenDto> result = staffRefreshTokenService.getAllStaffRefreshTokens();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(staffRefreshTokenRepository).findAll();
    }

    @Test
    void getStaffRefreshTokenById_NotFound_ShouldThrowException() {
        when(staffRefreshTokenRepository.findById(staffId)).thenReturn(Optional.empty());
        when(messageSource.getMessage(any(), any(), any())).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> staffRefreshTokenService.getStaffRefreshTokenById(staffId));
    }

    @Test
    void updateStaffRefreshToken_Valid_ShouldUpdate() {
        // Arrange: find existing, map existing to dto, map updated back to entity, save, map result back
        when(staffRefreshTokenRepository.findById(staffId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);
        when(appMapper.toEntity(any(StaffRefreshTokenDto.class))).thenReturn(entity);
        when(staffRefreshTokenRepository.save(any())).thenReturn(entity);

        // Act
        StaffRefreshTokenDto result = staffRefreshTokenService.updateStaffRefreshToken(staffId, dto);

        // Assert
        assertNotNull(result);
        verify(staffRefreshTokenRepository).save(any());
    }

    @Test
    void updateStaffRefreshToken_NullInput_ShouldThrowException() {
        StaffRefreshTokenDto invalidDto = new StaffRefreshTokenDto();
        // Missing token and expiry
        assertThrows(BadRequestException.class, () -> staffRefreshTokenService.updateStaffRefreshToken(staffId, invalidDto));
    }

    @Test
    void createStaffRefreshToken_Existing_ShouldReturnExisting() {
        // Mock finding existing token
        when(staffRefreshTokenRepository.findById(staffId)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        StaffRefreshTokenDto result = staffRefreshTokenService.createStaffRefreshToken(dto);

        // Verify save was NEVER called
        verify(staffRefreshTokenRepository, never()).save(any());
        assertEquals(dto, result);
    }

    @Test
    void createStaffRefreshToken_New_ShouldSave() {
        // Mock not finding existing token
        when(staffRefreshTokenRepository.findById(staffId)).thenReturn(Optional.empty());
        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(staffRefreshTokenRepository.save(entity)).thenReturn(entity);
        when(appMapper.toDto(entity)).thenReturn(dto);

        StaffRefreshTokenDto result = staffRefreshTokenService.createStaffRefreshToken(dto);

        assertNotNull(result);
        verify(staffRefreshTokenRepository).save(entity);
    }

    @Test
    void createStaffRefreshToken_NoStaffId_ShouldThrowException() {
        StaffRefreshTokenDto emptyDto = new StaffRefreshTokenDto();
        assertThrows(BadRequestException.class, () -> staffRefreshTokenService.createStaffRefreshToken(emptyDto));
    }

    @Test
    void getStaffRefreshTokenByToken_ShouldReturnDto() {
        String token = "some-token";
        when(staffRefreshTokenRepository.getByToken(token)).thenReturn(Optional.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        Optional<StaffRefreshTokenDto> result = staffRefreshTokenService.getStaffRefreshTokenByToken(token);

        assertTrue(result.isPresent());
        assertEquals(dto, result.get());
    }

    @Test
    void deleteAllByStaffIdIn_ShouldCallRepo() {
        Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());

        staffRefreshTokenService.deleteAllByStaffIdIn(ids);

        verify(staffRefreshTokenRepository).deleteAllByIdInBatch(ids);
    }

    @Test
    void deleteStaffRefreshToken_ShouldCallRepo() {
        staffRefreshTokenService.deleteStaffRefreshToken(staffId);
        verify(staffRefreshTokenRepository).deleteById(staffId);
    }
}