package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffSelfDeclaredSkillDto;
import com.tbm.careerpathlearning.enums.SkillProficiency;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffSelfDeclaredSkill;
import com.tbm.careerpathlearning.repository.StaffSelfDeclaredSkillRepository;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffSelfDeclaredSkillServiceImplTest {

    @Mock private StaffSelfDeclaredSkillRepository repository;
    @Mock private MessageSource messageSource;
    @Mock private ValidationService validationService;
    @Mock private AppMapper appMapper;

    @InjectMocks
    private StaffSelfSelfDeclaredSkillServiceImpl service;

    private StaffSelfDeclaredSkill entity;
    private StaffSelfDeclaredSkillDto dto;
    private UUID staffId;
    private Long skillId;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        skillId = 1L;

        Staff staff = new Staff();
        staff.setId(staffId);
        StaffDto staffDto = new StaffDto();
        staffDto.setId(staffId);

        entity = new StaffSelfDeclaredSkill();
        entity.setId(skillId);
        entity.setStaff(staff);
        entity.setSkill("Java");
        entity.setProficiency(SkillProficiency.INTERMEDIATE);

        dto = new StaffSelfDeclaredSkillDto();
        dto.setId(skillId);
        dto.setStaff(staffDto);
        dto.setSkill("Java");
        dto.setProficiency(SkillProficiency.INTERMEDIATE);

        lenient().when(messageSource.getMessage(any(), any(), any())).thenReturn("Error");
    }

    // --- Find Tests ---

    @Test
    void findById_ShouldReturnDto_WhenExists() {
        doReturn(Optional.of(entity)).when(repository).findById(skillId);
        when(appMapper.toDto(entity)).thenReturn(dto);

        assertNotNull(service.findById(skillId));
    }

    @Test
    void findById_ShouldThrowException_WhenNotFound() {
        doReturn(Optional.empty()).when(repository).findById(skillId);
        assertThrows(BadRequestException.class, () -> service.findById(skillId));
    }

    @Test
    void findByStaffId_ShouldReturnList() {
        when(repository.findAllByStaff_Id(staffId)).thenReturn(List.of(entity));
        when(appMapper.toDto(entity)).thenReturn(dto);

        assertFalse(service.findByStaffId(staffId).isEmpty());
    }

    // --- Create Tests ---

    @Test
    void create_ShouldSave_WhenValidAndUnique() {
        when(validationService.isNullOrBlank("Java")).thenReturn(false);
        // Ensure no existing skill with same name
        doReturn(Optional.empty()).when(repository).findByStaff_IdAndSkillIgnoreCase(staffId, "Java");

        when(appMapper.toEntity(dto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(appMapper.toDto(entity)).thenReturn(dto);

        assertNotNull(service.create(dto));
        verify(repository).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenDuplicateSkill() {
        when(validationService.isNullOrBlank("Java")).thenReturn(false);
        // Mock existing skill found
        doReturn(Optional.of(entity)).when(repository).findByStaff_IdAndSkillIgnoreCase(staffId, "Java");
        when(appMapper.toDto(entity)).thenReturn(dto); // Used inside getByStaffId check

        assertThrows(BadRequestException.class, () -> service.create(dto));
        verify(repository, never()).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenValidationFails() {
        dto.setSkill(""); // Invalid
        when(validationService.isNullOrBlank("")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.create(dto));
    }

    @Test
    void create_ShouldThrowException_WhenStaffIsNull() {
        dto.setStaff(null);
        assertThrows(BadRequestException.class, () -> service.create(dto));
    }

    @Test
    void create_ShouldThrowException_WhenProficiencyIsNull() {
        dto.setProficiency(null);
        // Ensure skill validation passes so we hit the proficiency check
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.create(dto));
    }

    @Test
    void create_ShouldThrowException_WhenSkillNameTooLong() {
        String longSkill = "a".repeat(256); // Max 255
        dto.setSkill(longSkill);
        dto.setProficiency(SkillProficiency.BEGINNER);
        when(validationService.isNullOrBlank(longSkill)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.create(dto));
    }

    @Test
    void create_ShouldThrowException_WhenDescriptionTooLong() {
        String longDesc = "a".repeat(1001); // Max 1000
        dto.setSkill("Java");
        dto.setProficiency(SkillProficiency.BEGINNER);
        dto.setDescription(longDesc);
        when(validationService.isNullOrBlank("Java")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.create(dto));
    }

    // --- Update Tests ---

    @Test
    void update_ShouldSave_WhenValid() {
        dto.setSkill("Python");
        when(validationService.isNullOrBlank("Python")).thenReturn(false);

        // Ensure no duplicate (or duplicate is self)
        doReturn(Optional.empty()).when(repository).findByStaff_IdAndSkillIgnoreCase(staffId, "Python");

        // Find existing to update
        doReturn(Optional.of(entity)).when(repository).findById(skillId);
        when(appMapper.toDto(entity)).thenReturn(dto);

        when(appMapper.toEntity(any(StaffSelfDeclaredSkillDto.class))).thenReturn(entity);
        when(repository.save(any())).thenReturn(entity);

        assertNotNull(service.update(skillId, dto));
    }

    @Test
    void update_ShouldThrowException_WhenDuplicateNameExists() {
        dto.setSkill("ExistingSkill");
        when(validationService.isNullOrBlank("ExistingSkill")).thenReturn(false);

        StaffSelfDeclaredSkillDto existingDto = new StaffSelfDeclaredSkillDto();
        existingDto.setId(999L); // Different ID

        // Mock finding a DIFFERENT skill with the same name
        StaffSelfDeclaredSkill existingEntity = new StaffSelfDeclaredSkill();
        existingEntity.setId(999L);

        doReturn(Optional.of(existingEntity)).when(repository).findByStaff_IdAndSkillIgnoreCase(staffId, "ExistingSkill");
        when(appMapper.toDto(existingEntity)).thenReturn(existingDto);

        assertThrows(BadRequestException.class, () -> service.update(skillId, dto));
    }

    @Test
    void update_ShouldThrowException_WhenIdIsNull() {
        assertThrows(BadRequestException.class, () -> service.update(null, dto));
    }

    // --- Delete Tests ---

    @Test
    void delete_ShouldCallRepository() {
        service.delete(skillId);
        verify(repository).deleteById(skillId);
    }
}