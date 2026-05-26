package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffLoginAuditDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.StaffLoginAudit;
import com.tbm.careerpathlearning.repository.StaffLoginAuditRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffLoginAuditServiceTest {

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private AppMapper appMapper;
    @Mock
    private StaffLoginAuditRepository staffLoginAuditRepository;

    @InjectMocks
    private StaffLoginAuditServiceImpl staffLoginAuditService;

    private UUID staffId;
    private StaffLoginAudit auditEntity;
    private StaffLoginAuditDto auditDto;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();

        auditEntity = new StaffLoginAudit();
        auditEntity.setStaffId(staffId);
        auditEntity.setLoginFailedAttempts(0);

        auditDto = new StaffLoginAuditDto();
        auditDto.setStaffId(staffId);
        auditDto.setLoginFailedAttempts(1);
        auditDto.setLastLoginAt(OffsetDateTime.now());
    }

    @Test
    void getAllStaffLoginAudits_ShouldReturnList() {
        when(staffLoginAuditRepository.findAll()).thenReturn(List.of(auditEntity));
        when(appMapper.toDto(auditEntity)).thenReturn(auditDto);

        List<StaffLoginAuditDto> result = staffLoginAuditService.getAllStaffLoginAudits();

        assertThat(result).hasSize(1);
        verify(staffLoginAuditRepository).findAll();
    }

    @Test
    void getStaffLoginAuditById_NotFound_ShouldThrowException() {
        when(staffLoginAuditRepository.findById(staffId)).thenReturn(Optional.empty());
        when(messageSource.getMessage(any(), any(), any())).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> staffLoginAuditService.getStaffLoginAuditById(staffId));
    }

    @Test
    void updateStaffLoginAudit_Valid_ShouldUpdate() {
        when(staffLoginAuditRepository.findById(staffId)).thenReturn(Optional.of(auditEntity));
        when(appMapper.toDto(auditEntity)).thenReturn(auditDto);
        when(appMapper.toEntity(any(StaffLoginAuditDto.class))).thenReturn(auditEntity);
        when(staffLoginAuditRepository.save(any())).thenReturn(auditEntity);
        when(appMapper.toDto(auditEntity)).thenReturn(auditDto);

        StaffLoginAuditDto result = staffLoginAuditService.updateStaffLoginAudit(staffId, auditDto);

        assertThat(result).isNotNull();
        assertEquals(1, result.getLoginFailedAttempts());
        verify(staffLoginAuditRepository).save(any());
    }

    @Test
    void updateStaffLoginAudit_NullId_ShouldThrowBadRequest() {
        assertThrows(BadRequestException.class, () ->
                staffLoginAuditService.updateStaffLoginAudit(null, auditDto));
    }

    @Test
    void createStaffLoginAudit_Existing_ShouldReturnExisting() {
        when(staffLoginAuditRepository.findById(staffId)).thenReturn(Optional.of(auditEntity));
        when(appMapper.toDto(auditEntity)).thenReturn(auditDto);

        StaffLoginAuditDto result = staffLoginAuditService.createStaffLoginAudit(auditDto);

        verify(staffLoginAuditRepository, never()).save(any());
        assertThat(result).isEqualTo(auditDto);
    }

    @Test
    void createStaffLoginAudit_New_ShouldSave() {
        when(staffLoginAuditRepository.findById(staffId)).thenReturn(Optional.empty());
        when(appMapper.toEntity(auditDto)).thenReturn(auditEntity);
        when(staffLoginAuditRepository.save(auditEntity)).thenReturn(auditEntity);
        when(appMapper.toDto(auditEntity)).thenReturn(auditDto);

        StaffLoginAuditDto result = staffLoginAuditService.createStaffLoginAudit(auditDto);

        verify(staffLoginAuditRepository).save(auditEntity);
        assertThat(result).isEqualTo(auditDto);
    }

    @Test
    void createStaffLoginAudit_NoStaffId_ShouldThrowBadRequest() {
        auditDto.setStaffId(null);
        assertThrows(BadRequestException.class, () -> staffLoginAuditService.createStaffLoginAudit(auditDto));
    }

    @Test
    void deleteStaffLoginAudit_ShouldCallRepo() {
        staffLoginAuditService.deleteStaffLoginAudit(staffId);
        verify(staffRepository).deleteById(staffId);
    }

    @Test
    void deleteAllByStaffIdIn_ShouldCallRepo() {
        Set<UUID> ids = Set.of(staffId);
        staffLoginAuditService.deleteAllByStaffIdIn(ids);
        verify(staffRepository).deleteAllByIdInBatch(ids);
    }
}