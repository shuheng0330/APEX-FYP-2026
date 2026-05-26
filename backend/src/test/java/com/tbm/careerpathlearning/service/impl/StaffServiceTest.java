package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.ParentChildNodeService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @InjectMocks
    private StaffServiceImpl staffService;

    @Mock private StaffRepository staffRepository;
    @Mock private MessageSource messageSource;
    @Mock private ValidationService validationService;
    @Mock private ParentChildNodeService parentChildNodeService;
    @Mock private AppMapper appMapper;

    private Staff staffEntity;
    private StaffDto staffDto;
    private final UUID STAFF_ID = UUID.randomUUID();
    private final UUID MANAGER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        staffEntity = new Staff();
        staffEntity.setId(STAFF_ID);
        staffEntity.setName("John Doe");
        staffEntity.setEmail("john@test.com");
        staffEntity.setPassword("encodedPassword");

        staffDto = new StaffDto();
        staffDto.setId(STAFF_ID);
        staffDto.setName("John Doe");
        staffDto.setEmail("john@test.com");
        staffDto.setPassword("plainPassword");

        // Common leniency for MessageSource to avoid unnecessary stubbing errors
        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Error Message");
    }

    // --- FIND Tests ---

    @Test
    void findAll_ShouldReturnListWithNullPasswords() {
        when(staffRepository.findAll()).thenReturn(List.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        List<StaffDto> result = staffService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getPassword()); // Password should be cleared
    }

    @Test
    void findById_ShouldReturnDto_WhenExists() {
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        StaffDto result = staffService.findById(STAFF_ID);

        assertEquals(STAFF_ID, result.getId());
        assertNull(result.getPassword());
    }

    @Test
    void findById_ShouldThrowDataAccessException_WhenNotFound() {
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.empty());

        assertThrows(DataAccessException.class, () -> staffService.findById(STAFF_ID));
    }

    @Test
    void findByEmail_ShouldReturnDto() {
        String email = "john@test.com";
        when(staffRepository.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        Optional<StaffDto> result = staffService.findByIsDeletedIsFalseAndEmail(email);

        assertTrue(result.isPresent());
        assertEquals(email, result.get().getEmail());
    }

    @Test
    void findAllByCareerPathwayId_ShouldReturnList() {
        Long pathwayId = 100L;
        when(staffRepository.findAllByCareerPathway_Id(pathwayId)).thenReturn(List.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        List<StaffDto> result = staffService.findAllByCareerPathwayId(pathwayId);

        assertFalse(result.isEmpty());
        assertNull(result.get(0).getPassword());
    }

    // --- CREATE Tests ---

    @Test
    void create_ShouldSaveAndReturnDto_WhenValid() {
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffRepository.findByIsDeletedIsFalseAndEmail(anyString())).thenReturn(Optional.empty());
        when(appMapper.toEntity(staffDto)).thenReturn(staffEntity);
        when(staffRepository.save(staffEntity)).thenReturn(staffEntity);
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        StaffDto result = staffService.create(staffDto);

        assertNotNull(result);
        assertNull(result.getPassword());
        verify(staffRepository).save(staffEntity);
    }

    @Test
    void create_ShouldThrowBadRequest_WhenEmailExists() {
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        // Simulate existing email
        when(staffRepository.findByIsDeletedIsFalseAndEmail(staffDto.getEmail())).thenReturn(Optional.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        assertThrows(BadRequestException.class, () -> staffService.create(staffDto));
    }

    @Test
    void create_ShouldThrowBadRequest_WhenDataInvalid() {
        staffDto.setEmail(""); // Invalid
        when(validationService.isNullOrBlank("")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> staffService.create(staffDto));
    }

    // --- UPDATE Tests ---

    @Test
    void update_ShouldUpdateFieldsAndReturnDto_WhenValid() {
        UUID id = STAFF_ID;
        StaffDto updateRequest = new StaffDto();
        updateRequest.setName("Jane Doe");
        updateRequest.setEmail("jane@test.com");

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffRepository.findById(id)).thenReturn(Optional.of(staffEntity));
        // Email check: No conflict
        when(staffRepository.findByIsDeletedIsFalseAndEmail("jane@test.com")).thenReturn(Optional.empty());

        when(appMapper.toDto(any(Staff.class))).thenReturn(staffDto); // Stub mapper calls
        when(appMapper.toEntity(any(StaffDto.class))).thenReturn(staffEntity);
        when(staffRepository.save(any(Staff.class))).thenReturn(staffEntity);

        StaffDto result = staffService.update(id, updateRequest);

        assertNotNull(result);
        verify(staffRepository).save(any(Staff.class));
    }

    @Test
    void update_ShouldThrowBadRequest_WhenEmailConflictExists() {
        UUID otherId = UUID.randomUUID();
        StaffDto otherStaff = new StaffDto();
        otherStaff.setId(otherId); // Different ID
        otherStaff.setEmail("conflict@test.com");

        StaffDto updateRequest = new StaffDto();
        updateRequest.setEmail("conflict@test.com");

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);

        // Mock finding ANOTHER staff with the requested email
        when(staffRepository.findByIsDeletedIsFalseAndEmail("conflict@test.com")).thenReturn(Optional.of(staffEntity));
        // We need the mocked return of findByIsDeletedIsFalseAndEmail to have the DIFFERENT ID
        Staff otherStaffEntity = new Staff(); otherStaffEntity.setId(otherId);
        when(staffRepository.findByIsDeletedIsFalseAndEmail("conflict@test.com")).thenReturn(Optional.of(otherStaffEntity));
        when(appMapper.toDto(otherStaffEntity)).thenReturn(otherStaff);

        assertThrows(BadRequestException.class, () -> staffService.update(STAFF_ID, updateRequest));
    }

    // --- BULK UPDATE Tests ---

    @Test
    void updateAll_ShouldUpdateList_WhenValid() {
        List<StaffDto> dtos = List.of(staffDto);

        // Mock existing staffs map
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto); // For map creation inside service

        when(appMapper.toEntity(any(StaffDto.class))).thenReturn(staffEntity);
        when(staffRepository.saveAll(anyList())).thenReturn(List.of(staffEntity));

        List<StaffDto> result = staffService.updateAll(dtos);

        assertFalse(result.isEmpty());
        verify(staffRepository).saveAll(anyList());
    }

    @Test
    void updateAll_ShouldThrow_WhenDuplicateEmailsInList() {
        StaffDto s1 = new StaffDto(); s1.setEmail("same@test.com");
        StaffDto s2 = new StaffDto(); s2.setEmail("same@test.com");

        assertThrows(BadRequestException.class, () -> staffService.updateAll(List.of(s1, s2)));
    }

    // --- DELETE Tests ---

    @Test
    void delete_ShouldSoftDelete() {
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);
        when(appMapper.toEntity(staffDto)).thenReturn(staffEntity);

        staffService.delete(STAFF_ID, MANAGER_ID);

        assertTrue(staffDto.isDeleted());
        assertEquals(MANAGER_ID, staffDto.getUpdatedBy());

        verify(staffRepository).save(staffEntity);
    }

    @Test
    void deleteAllById_ShouldSoftDeleteAll() {
        Set<UUID> ids = Set.of(STAFF_ID);
        when(staffRepository.findAllById(ids)).thenReturn(List.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);
        when(appMapper.toEntity(staffDto)).thenReturn(staffEntity);

        staffService.deleteAllById(ids, MANAGER_ID);

        assertTrue(staffDto.isDeleted());
        assertEquals(MANAGER_ID, staffDto.getUpdatedBy());

        verify(staffRepository).saveAll(anyList());
    }

    // --- HIERARCHY / DOWNLINE Tests ---

    @Test
    void getAllDownlineStaffIds_ShouldReturnSubordinatesAndOrgDescendants() {
        // Setup User
        UUID userId = UUID.randomUUID();
        StaffDto user = new StaffDto(); user.setId(userId);

        OrgChartDto userOrg = new OrgChartDto(); userOrg.setId(10L);
        RoleDto userRole = new RoleDto(); userRole.setOrgChart(userOrg);
        user.setRole(userRole);

        // Setup Downline via Org Chart (Child Dept)
        Long childOrgId = 20L;
        ParentChildNodeDto orgRel = new ParentChildNodeDto(); orgRel.setParentId(10L); orgRel.setChildId(childOrgId);

        StaffDto deptChild = new StaffDto(); deptChild.setId(UUID.randomUUID());
        RoleDto childRole = new RoleDto(); childRole.setOrgChart(
                new OrgChartDto(childOrgId, OrgChartType.D, "Child Dept", false, false, null, OffsetDateTime.now(), null , OffsetDateTime.now())
        );
        deptChild.setRole(childRole);

        // Setup Downline via Direct Report (Manager Assignment)
        StaffDto subordinate = new StaffDto(); subordinate.setId(UUID.randomUUID());
        subordinate.setManager(user); // Directly reports to user

        // Mock Data
        List<StaffDto> allStaffs = List.of(user, deptChild, subordinate);
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(new Staff(), new Staff(), new Staff()));
        // We mock appMapper to return our specific DTOs above in order
        when(appMapper.toDto(any(Staff.class))).thenReturn(user, deptChild, subordinate);

        when(parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(List.of(orgRel));

        Set<UUID> result = staffService.getAllDownlineStaffIds(userId);

        assertTrue(result.contains(userId)); // Self
        assertTrue(result.contains(deptChild.getId())); // From Child Department
        assertTrue(result.contains(subordinate.getId())); // Direct Report
    }

    // --- Helper for updateRoleByStaffIdIn ---

    @Test
    void updateRoleByStaffIdIn_ShouldUpdateRoles() {
        Set<UUID> ids = Set.of(STAFF_ID);
        RoleDto newRole = new RoleDto(); newRole.setId(99L);

        when(staffRepository.findAllById(ids)).thenReturn(List.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);
        when(appMapper.toEntity(staffDto)).thenReturn(staffEntity);
        when(staffRepository.saveAll(anyList())).thenReturn(List.of(staffEntity));

        List<StaffDto> result = staffService.updateRoleByStaffIdIn(ids, newRole, MANAGER_ID);

        assertEquals(newRole, staffDto.getRole());
        assertEquals(MANAGER_ID, staffDto.getUpdatedBy());

        assertFalse(result.isEmpty());
        // Only verify the mock repository interaction
        verify(staffRepository).saveAll(anyList());
    }

    // --- Helper for updateCareerPathwayByIdIn ---

    @Test
    void updateCareerPathwayByIdIn_ShouldUpdatePathway() {
        Set<UUID> ids = Set.of(STAFF_ID);
        CareerPathwayDto newPath = new CareerPathwayDto(); newPath.setId(55L);

        when(staffRepository.findAllById(ids)).thenReturn(List.of(staffEntity));
        when(appMapper.toDto(staffEntity)).thenReturn(staffDto);
        when(appMapper.toEntity(staffDto)).thenReturn(staffEntity);
        when(staffRepository.saveAll(anyList())).thenReturn(List.of(staffEntity));

        List<StaffDto> result = staffService.updateCareerPathwayByIdIn(ids, newPath, MANAGER_ID, OffsetDateTime.now());

        assertEquals(newPath, staffDto.getCareerPathway());
        assertEquals(MANAGER_ID, staffDto.getUpdatedBy());

        assertFalse(result.isEmpty());

        // You CAN verify the repository because it IS a mock
        verify(staffRepository).saveAll(anyList());
    }
}