package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.repository.RoleRepository;
import com.tbm.careerpathlearning.service.ValidationService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AppMapper appMapper;
    @Mock
    private MessageSource messageSource;
    @Mock
    private ValidationService validationService;

    @InjectMocks
    private RoleServiceImpl roleService;

    private Role mockRole;
    private RoleDto mockRoleDto;
    private OrgChartDto mockOrgChartDto;
    private Long roleId = 1L;
    private Long orgId = 100L;
    private UUID userUUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockOrgChartDto = new OrgChartDto();
        mockOrgChartDto.setId(orgId);
        mockOrgChartDto.setName("IT Dept");

        mockRole = new Role();
        mockRole.setId(roleId);
        mockRole.setName("Developer");
        mockRole.setDeleted(false);
        mockRole.setOrgChart(new OrgChart());
        mockRole.getOrgChart().setId(orgId);

        mockRoleDto = new RoleDto();
        mockRoleDto.setId(roleId);
        mockRoleDto.setName("Developer");
        mockRoleDto.setOrgChart(mockOrgChartDto);
        mockRoleDto.setDeleted(false);
        mockRoleDto.setCreatedBy(userUUID);
        mockRoleDto.setUpdatedBy(userUUID);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void getAll_ShouldReturnList() {
        when(roleRepository.findAll()).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        assertFalse(roleService.getAll().isEmpty());
    }

    @Test
    void getAllById_ShouldReturnDto_WhenExists() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        assertNotNull(roleService.getAllById(roleId));
    }

    @Test
    void getAllById_ShouldThrowException_WhenNotFound() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());
        assertThrows(DataAccessException.class, () -> roleService.getAllById(roleId));
    }

    // --- Create Tests ---

    @Test
    void create_ShouldSave_WhenValidAndUnique() {
        when(validationService.isNullOrBlank("Developer")).thenReturn(false);
        // Mock redundancy check (empty means no duplicate found)
        when(roleRepository.findAllByIsDeletedIsFalseAndOrgChart_IdAndNameIgnoreCase(orgId, "Developer"))
                .thenReturn(Optional.empty());

        when(appMapper.toEntity(mockRoleDto)).thenReturn(mockRole);
        when(roleRepository.save(mockRole)).thenReturn(mockRole);
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        assertNotNull(roleService.create(mockRoleDto));
        verify(roleRepository).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenNameDuplicateInSameDepartment() {
        when(validationService.isNullOrBlank("Developer")).thenReturn(false);
        // Mock finding an existing role with same name in same dept
        when(roleRepository.findAllByIsDeletedIsFalseAndOrgChart_IdAndNameIgnoreCase(orgId, "Developer"))
                .thenReturn(Optional.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        assertThrows(DataAccessException.class, () -> roleService.create(mockRoleDto));
    }

    @Test
    void create_ShouldThrowException_WhenValidationFails() {
        mockRoleDto.setName(null);
        when(validationService.isNullOrBlank(null)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> roleService.create(mockRoleDto));
    }

    // --- Create All (Bulk) Tests ---

    @Test
    void createAll_ShouldSaveList_WhenValid() {
        List<RoleDto> dtos = List.of(mockRoleDto);
        when(roleService.getAllByDeletedIsFalse()).thenReturn(Collections.emptyList()); // No existing roles
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        when(appMapper.toEntity(any(RoleDto.class))).thenReturn(mockRole);
        when(roleRepository.saveAll(anyList())).thenReturn(List.of(mockRole));
        when(appMapper.toDto(any(Role.class))).thenReturn(mockRoleDto);

        List<RoleDto> result = roleService.createAll(dtos);
        assertFalse(result.isEmpty());
    }

    @Test
    void createAll_ShouldThrowException_WhenInputListHasDuplicates() {
        // List contains two roles with same name "Developer" for same Org ID
        List<RoleDto> dtos = List.of(mockRoleDto, mockRoleDto);
        when(roleService.getAllByDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> roleService.createAll(dtos));
    }

    @Test
    void createAll_ShouldThrowException_WhenListIsEmpty() {
        assertThrows(BadRequestException.class, () -> roleService.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ShouldThrowException_WhenOneDtoIsInvalid() {
        // Arrange
        RoleDto invalidDto = new RoleDto();
        invalidDto.setName(""); // Invalid
        List<RoleDto> dtos = List.of(mockRoleDto, invalidDto);

        when(roleService.getAllByDeletedIsFalse()).thenReturn(Collections.emptyList());
        // Mock validation to fail for the empty string
        when(validationService.isNullOrBlank("")).thenReturn(true);
        when(validationService.isNullOrBlank("Developer")).thenReturn(false);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> roleService.createAll(dtos));
    }

    @Test
    void createAll_ShouldThrowException_WhenNameAlreadyExistsInDb() {
        // Arrange
        List<RoleDto> dtos = List.of(mockRoleDto); // "Developer"

        // Mock DB returning an existing role with the same name/orgId
        when(roleRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> roleService.createAll(dtos));
    }

    // --- Update Tests ---

    @Test
    void update_ShouldSave_WhenValid() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(mockRole)); // for getById
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);
        when(validationService.isNullOrBlank("Developer")).thenReturn(false);

        // Redundancy check: returns self (same ID) -> Allowed
        when(roleRepository.findAllByIsDeletedIsFalseAndOrgChart_IdAndNameIgnoreCase(orgId, "Developer"))
                .thenReturn(Optional.of(mockRole)); // Returning entity to allow mapping to DTO inside the check logic

        when(appMapper.toEntity(any(RoleDto.class))).thenReturn(mockRole);
        when(roleRepository.save(mockRole)).thenReturn(mockRole);

        assertNotNull(roleService.update(roleId, mockRoleDto));
    }

    @Test
    void update_ShouldThrowException_WhenDuplicateNameExistsWithDifferentId() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);
        when(validationService.isNullOrBlank("Developer")).thenReturn(false);

        // Simulate existing role with SAME name but DIFFERENT ID
        Role existingOther = new Role();
        existingOther.setId(999L);
        existingOther.setName("Developer");
        RoleDto existingOtherDto = new RoleDto();
        existingOtherDto.setId(999L);
        existingOtherDto.setName("Developer");

        when(roleRepository.findAllByIsDeletedIsFalseAndOrgChart_IdAndNameIgnoreCase(orgId, "Developer"))
                .thenReturn(Optional.of(existingOther));
        when(appMapper.toDto(existingOther)).thenReturn(existingOtherDto);

        assertThrows(DataAccessException.class, () -> roleService.update(roleId, mockRoleDto));
    }

    @Test
    void update_ShouldThrowException_WhenOrgChartIsNull() {
        mockRoleDto.setOrgChart(null);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        assertThrows(BadRequestException.class, () -> roleService.update(roleId, mockRoleDto));
    }

    @Test
    void update_ShouldThrowException_WhenNameTooLong() {
        mockRoleDto.setName("A".repeat(256));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> roleService.update(roleId, mockRoleDto));
    }

    // --- Update All Tests ---

    @Test
    void updateAll_ShouldSave_WhenValid() {
        // Arrange
        Set<Long> ids = Set.of(roleId);
        List<RoleDto> dtos = List.of(mockRoleDto);

        // Mock finding existing entities
        when(roleRepository.findAllByIdInAndIsDeletedIsFalse(ids)).thenReturn(List.of(mockRole));
        // Mock DB check for duplicates (returns empty list of other roles)
        when(roleRepository.findAllById(ids)).thenReturn(List.of(mockRole));

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(appMapper.toEntity(any(RoleDto.class))).thenReturn(mockRole);
        when(roleRepository.saveAll(anyList())).thenReturn(List.of(mockRole));
        when(appMapper.toDto(any(Role.class))).thenReturn(mockRoleDto);

        // Act
        List<RoleDto> result = roleService.updateAll(ids, dtos);

        // Assert
        assertNotNull(result);
        verify(roleRepository).saveAll(anyList());
    }

    @Test
    void updateAll_ShouldThrowException_WhenDuplicateNamesInInputList() {
        // Arrange: Input list has two "Developer" roles
        List<RoleDto> dtos = List.of(mockRoleDto, mockRoleDto);
        Set<Long> ids = Set.of(roleId);

        // Mock DB lookup for redundancy check
        when(roleRepository.findAllById(any())).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> roleService.updateAll(ids, dtos));
    }

    @Test
    void updateAll_ShouldThrowException_WhenOrgChartIsNullInDto() {
        // Arrange
        RoleDto invalidDto = new RoleDto();
        invalidDto.setId(roleId);
        invalidDto.setName("Valid Name");
        invalidDto.setOrgChart(null); // NULL OrgChart

        Set<Long> ids = Set.of(roleId);
        List<RoleDto> dtos = List.of(invalidDto);

        when(roleRepository.findAllByIdInAndIsDeletedIsFalse(ids)).thenReturn(List.of(mockRole));
        when(roleRepository.findAllById(ids)).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto); // needed for redundancy check setup

        assertThrows(BadRequestException.class, () -> roleService.updateAll(ids, dtos));
    }

    // --- Create and Update All (Mixed) Tests ---

    @Test
    void createAndUpdateAll_ShouldHandleMixedOperations() {
        // 1. Existing Role (To Update)
        RoleDto existingDto = new RoleDto();
        existingDto.setId(roleId);
        existingDto.setName("Updated Name");
        existingDto.setOrgChart(mockOrgChartDto);

        // 2. New Role (To Create - ID Null)
        RoleDto newDto = new RoleDto();
        newDto.setName("New Role");
        newDto.setOrgChart(mockOrgChartDto);

        List<RoleDto> inputList = List.of(existingDto, newDto);

        // Mock getting existing roles from DB
        lenient().when(roleRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole));
        lenient().when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(roleRepository.saveAll(anyList())).thenReturn(List.of(mockRole, new Role()));
        when(appMapper.toDto(any(Role.class))).thenReturn(mockRoleDto);

        assertNotNull(roleService.createAndUpdateAll(inputList));
        verify(roleRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenListIsEmpty() {
        assertThrows(BadRequestException.class, () -> roleService.createAndUpdateAll(Collections.emptyList()));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenDuplicateInInputList() {
        // Arrange:
        RoleDto dto1 = new RoleDto(); dto1.setName("SameName"); dto1.setOrgChart(mockOrgChartDto);
        RoleDto dto2 = new RoleDto(); dto2.setName("SameName"); dto2.setOrgChart(mockOrgChartDto);

        List<RoleDto> dtos = List.of(dto1, dto2);

        when(roleRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // Act & Assert
        assertThrows(BadRequestException.class, () -> roleService.createAndUpdateAll(dtos));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenNewRecordNameExistsInDb() {
        // Arrange: New DTO (ID null) with name "Developer"
        RoleDto newDto = new RoleDto();
        newDto.setName("Developer");
        newDto.setOrgChart(mockOrgChartDto);

        // DB already has "Developer"
        when(roleRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> roleService.createAndUpdateAll(List.of(newDto)));
    }

    // --- Delete Tests ---

    @Test
    void delete_ShouldSoftDelete() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        when(appMapper.toEntity(any(RoleDto.class))).thenAnswer(invocation -> {
            RoleDto dto = invocation.getArgument(0);
            Role r = new Role();
            r.setId(dto.getId());
            r.setDeleted(dto.isDeleted());
            return r;
        });

        roleService.delete(roleId, userUUID);

        verify(roleRepository).save(argThat(Role::isDeleted));
    }

    @Test
    void deleteAllByRoleIdIn_ShouldSoftDeleteAll() {
        Set<Long> ids = Set.of(roleId);
        when(roleRepository.findAllByIdInAndIsDeletedIsFalse(ids)).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        when(appMapper.toEntity(any(RoleDto.class))).thenAnswer(inv -> {
            RoleDto dto = inv.getArgument(0);
            Role r = new Role();
            r.setDeleted(dto.isDeleted());
            return r;
        });

        roleService.deleteAllByRoleIdIn(ids, userUUID);

        verify(roleRepository).saveAll(argThat(list -> {
            List<Role> roles = (List<Role>) list;
            // Now this won't be null
            return !roles.isEmpty() && roles.get(0).isDeleted();
        }));
    }

    @Test
    void findAndDeleteAllByOrgChartIdIn_ShouldReturnDeletedDtos() {
        Set<Long> orgIds = Set.of(orgId);
        when(roleRepository.findAllByOrgChartIdInAndIsDeletedIsFalse(orgIds)).thenReturn(List.of(mockRole));
        when(appMapper.toDto(mockRole)).thenReturn(mockRoleDto);

        List<RoleDto> result = roleService.findAndDeleteAllByOrgChartIdIn(orgIds, userUUID, OffsetDateTime.now());

        assertFalse(result.isEmpty());
        verify(roleRepository).saveAll(anyList());
    }

    // --- Validation Edge Cases ---

    @Test
    void create_ShouldThrowException_WhenOrgChartIsNull() {
        mockRoleDto.setOrgChart(null);
        assertThrows(BadRequestException.class, () -> roleService.create(mockRoleDto));
    }

    @Test
    void create_ShouldThrowException_WhenNameTooLong() {
        mockRoleDto.setName("A".repeat(256));
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        assertThrows(BadRequestException.class, () -> roleService.create(mockRoleDto));
    }

    @Test
    void create_ShouldThrowException_WhenDescriptionTooLong() {
        mockRoleDto.setDescription("A".repeat(1001));
        when(validationService.isNullOrBlank("Developer")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> roleService.create(mockRoleDto));
    }
}