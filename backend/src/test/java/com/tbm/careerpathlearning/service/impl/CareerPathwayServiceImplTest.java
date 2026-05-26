package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CareerPathwayDto;
import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CareerPathway;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.repository.CareerPathwayRepository;
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
class CareerPathwayServiceImplTest {

    @Mock
    private CareerPathwayRepository careerPathwayRepository;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ValidationService validationService;

    @InjectMocks
    private CareerPathwayServiceImpl careerPathwayService;

    private CareerPathway mockEntity;
    private CareerPathwayDto mockDto;
    private OrgChartDto mockOrgChartDto;
    private RoleDto mockRootRoleDto;
    private final UUID USER_ID = UUID.randomUUID();
    private final OffsetDateTime NOW = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        // Common setup for Organization and Role
        mockOrgChartDto = new OrgChartDto();
        mockOrgChartDto.setId(10L);
        mockOrgChartDto.setName("IT Department");

        mockRootRoleDto = new RoleDto();
        mockRootRoleDto.setId(100L);
        mockRootRoleDto.setName("Head of IT");
        mockRootRoleDto.setOrgChart(mockOrgChartDto); // Role belongs to IT Dept

        // Setup Career Pathway DTO
        mockDto = new CareerPathwayDto();
        mockDto.setId(1L);
        mockDto.setName("Software Engineering");
        mockDto.setOrgChart(mockOrgChartDto);
        mockDto.setRootRole(mockRootRoleDto);
        mockDto.setCreatedBy(USER_ID);
        mockDto.setCreatedAt(NOW);

        // Setup Career Pathway Entity
        mockEntity = new CareerPathway();
        mockEntity.setId(1L);
        mockEntity.setName("Software Engineering");
        OrgChart orgEntity = new OrgChart();
        orgEntity.setId(1L);
        mockEntity.setOrgChart(orgEntity);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error Message");
    }

    // --- Find Tests ---

    @Test
    void getAll_ShouldReturnList() {
        when(careerPathwayRepository.findAll()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayDto> result = careerPathwayService.getAll();
        assertEquals(1, result.size());
    }

    @Test
    void getAllByIsDeletedIsFalse_ShouldReturnList() {
        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayDto> result = careerPathwayService.getAllByIsDeletedIsFalse();
        assertEquals(1, result.size());
    }

    @Test
    void getById_ShouldReturnDto_WhenFound() {
        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        CareerPathwayDto result = careerPathwayService.getById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getById_ShouldThrowException_WhenNotFound() {
        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(DataAccessException.class, () -> careerPathwayService.getById(1L));
    }

    @Test
    void getAllByIsDeletedIsFalseAndIdIn_ShouldReturnList() {
        Set<Long> ids = Set.of(1L);
        when(careerPathwayRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<CareerPathwayDto> result = careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(ids);
        assertEquals(1, result.size());
    }

    // --- Create Tests ---

    @Test
    void create_ShouldSave_WhenValid() {
        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(appMapper.toEntity(mockDto)).thenReturn(mockEntity);
        when(careerPathwayRepository.save(mockEntity)).thenReturn(mockEntity);
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        CareerPathwayDto result = careerPathwayService.create(mockDto);
        assertNotNull(result);
        verify(careerPathwayRepository).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenOrgChartMismatch() {
        // Root Role belongs to Dept 2, but Pathway is for Dept 1
        OrgChartDto differentOrg = new OrgChartDto();
        differentOrg.setId(2L);
        mockRootRoleDto.setOrgChart(differentOrg);
        mockDto.setRootRole(mockRootRoleDto);

        assertThrows(BadRequestException.class, () -> careerPathwayService.create(mockDto));
    }

    @Test
    void create_ShouldThrowException_WhenNameDuplicateInSameOrg() {
        // Existing record: "Software Engineering" in Dept 1
        CareerPathway existing = new CareerPathway();
        existing.setName("Software Engineering");
        OrgChart org = new OrgChart();
        org.setId(10L);
        existing.setOrgChart(org);

        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(existing));

        // Attempt to create "Software Engineering" in Dept 1 again
        assertThrows(BadRequestException.class, () -> careerPathwayService.create(mockDto));
    }

    @Test
    void create_ShouldThrowException_WhenInvalidData() {
        mockDto.setName(""); // Invalid name
        assertThrows(BadRequestException.class, () -> careerPathwayService.create(mockDto));
    }

    // --- Update Tests ---

    @Test
    void update_ShouldSave_WhenValid() {
        CareerPathwayDto updateDto = new CareerPathwayDto();
        updateDto.setId(1L);
        updateDto.setName("New Name");
        updateDto.setOrgChart(mockOrgChartDto);
        updateDto.setRootRole(mockRootRoleDto);

        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.of(mockEntity)); // For getById check
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto); // For getById check

        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // No duplicates

        when(appMapper.toEntity(any(CareerPathwayDto.class))).thenReturn(mockEntity);
        when(careerPathwayRepository.save(mockEntity)).thenReturn(mockEntity);

        CareerPathwayDto result = careerPathwayService.update(1L, updateDto);
        assertNotNull(result);
    }

    @Test
    void update_ShouldThrowException_WhenDuplicateNameInSameOrg() {
        CareerPathwayDto updateDto = new CareerPathwayDto();
        updateDto.setId(1L);
        updateDto.setName("Existing Name");
        updateDto.setOrgChart(mockOrgChartDto); // ID 1
        updateDto.setRootRole(mockRootRoleDto); // ID 1

        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        // Another pathway (ID 2) exists with the name "Existing Name" in the same Org (ID 10)
        CareerPathway existingOther = new CareerPathway();
        existingOther.setId(2L);
        existingOther.setName("Existing Name");
        OrgChart org = new OrgChart();
        org.setId(10L);
        existingOther.setOrgChart(org);

        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(existingOther));

        assertThrows(BadRequestException.class, () -> careerPathwayService.update(1L, updateDto));
    }

    @Test
    void update_ShouldThrowException_WhenInvalidData() {
        CareerPathwayDto invalidDto = new CareerPathwayDto();
        invalidDto.setName(""); // Empty name
        invalidDto.setOrgChart(mockOrgChartDto);

        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertThrows(BadRequestException.class, () -> careerPathwayService.update(1L, invalidDto));
    }

    @Test
    void update_ShouldThrowException_WhenOrgChartMismatch() {
        CareerPathwayDto mismatchDto = new CareerPathwayDto();
        mismatchDto.setId(1L);
        mismatchDto.setName("Valid Name");

        // Pathway Org = 10L (from setUp)
        mismatchDto.setOrgChart(mockOrgChartDto);

        // Root Role Org = 99L (Mismatch)
        RoleDto roleWithDifferentOrg = new RoleDto();
        OrgChartDto diffOrg = new OrgChartDto();
        diffOrg.setId(99L);
        roleWithDifferentOrg.setOrgChart(diffOrg);

        mismatchDto.setRootRole(roleWithDifferentOrg);

        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        assertThrows(BadRequestException.class, () -> careerPathwayService.update(1L, mismatchDto));
    }

    // --- Bulk Create/Update Tests ---

    @Test
    void createAndUpdateAll_ShouldProcessValidList() {
        List<CareerPathwayDto> input = List.of(mockDto); // ID = 1

        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto); // Important for building the map

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        when(appMapper.toEntity(any(CareerPathwayDto.class))).thenReturn(mockEntity);
        when(careerPathwayRepository.saveAll(anyList())).thenReturn(List.of(mockEntity));

        List<CareerPathwayDto> result = careerPathwayService.createAndUpdateAll(input);
        assertEquals(1, result.size());
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenDuplicatesInInput() {
        // Two entries with same name "Software Engineering" for same Org ID 1
        CareerPathwayDto dup1 = new CareerPathwayDto();
        dup1.setName("Same");
        dup1.setOrgChart(mockOrgChartDto);
        dup1.setRootRole(mockRootRoleDto);
        CareerPathwayDto dup2 = new CareerPathwayDto();
        dup2.setName("Same");
        dup2.setOrgChart(mockOrgChartDto);
        dup2.setRootRole(mockRootRoleDto);

        List<CareerPathwayDto> input = List.of(dup1, dup2);

        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> careerPathwayService.createAndUpdateAll(input));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenDBConflict() {
        List<CareerPathwayDto> input = List.of(mockDto);

        // 1. The record being updated (ID 1) MUST exist in DB to pass the "Not Found" check
        CareerPathwayDto currentRecord = new CareerPathwayDto();
        currentRecord.setId(1L);
        currentRecord.setName("Old Name");
        currentRecord.setOrgChart(mockOrgChartDto);

        // 2. The conflicting record (ID 2) that already has the target name
        CareerPathwayDto conflictingDto = new CareerPathwayDto();
        conflictingDto.setId(2L);
        conflictingDto.setName("Software Engineering");
        conflictingDto.setOrgChart(mockOrgChartDto);

        // Mock DB returning BOTH records
        CareerPathwayServiceImpl spyService = spy(careerPathwayService);
        doReturn(List.of(currentRecord, conflictingDto)).when(spyService).getAllByIsDeletedIsFalse();

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // Now it should pass the ID check and fail at the Unique Name check
        assertThrows(BadRequestException.class, () -> spyService.createAndUpdateAll(input));
    }

    @Test
    void createAndUpdateAll_ShouldSkipValidation_WhenDeleting() {
        // Scenario: Trying to DELETE ID 1, but renaming it to "Software Engineering"
        // "Software Engineering" ALREADY EXISTS (ID 2).
        // This should pass (allow delete) but currently fails (checks unique name).

        CareerPathwayDto deleteRequest = new CareerPathwayDto();
        deleteRequest.setId(1L);
        deleteRequest.setName("Software Engineering"); // Name collision!
        deleteRequest.setDeleted(true);
        deleteRequest.setOrgChart(mockOrgChartDto);
        deleteRequest.setRootRole(mockRootRoleDto);

        // Existing DB Records
        CareerPathwayDto itemToDelete = new CareerPathwayDto();
        itemToDelete.setId(1L);
        itemToDelete.setName("Old Name");
        itemToDelete.setOrgChart(mockOrgChartDto);
        CareerPathwayDto existingConflict = new CareerPathwayDto();
        existingConflict.setId(2L);
        existingConflict.setName("Software Engineering");
        existingConflict.setOrgChart(mockOrgChartDto);

        CareerPathwayServiceImpl spyService = spy(careerPathwayService);
        doReturn(List.of(itemToDelete, existingConflict)).when(spyService).getAllByIsDeletedIsFalse();

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(appMapper.toEntity(any(CareerPathwayDto.class))).thenReturn(mockEntity);
        when(careerPathwayRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> spyService.createAndUpdateAll(List.of(deleteRequest)));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenIdNotFound() {
        // DTO has ID 99, but DB is empty
        CareerPathwayDto notFoundDto = new CareerPathwayDto();
        notFoundDto.setId(99L);
        notFoundDto.setName("Software Engineering");
        notFoundDto.setOrgChart(mockOrgChartDto);
        notFoundDto.setRootRole(mockRootRoleDto);

        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(DataAccessException.class, () -> careerPathwayService.createAndUpdateAll(List.of(notFoundDto)));
    }

    @Test
    void createAndUpdateAll_ShouldAllowNameReuse_WhenOriginalIsDeleted() {
        // Scenario:
        // 1. "Software Engineering" (ID 1) is being DELETED.
        // 2. A NEW record "Software Engineering" is being CREATED.
        // This should pass because the logic removes the name from the validation map when it sees the delete flag.

        // DB State: ID 1 exists with name "Software Engineering"
        when(careerPathwayRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockEntity));

        // Input 1: Delete ID 1
        CareerPathwayDto deleteDto = new CareerPathwayDto();
        deleteDto.setId(1L);
        deleteDto.setName("Software Engineering");
        deleteDto.setDeleted(true); // <--- Flagged for delete
        deleteDto.setOrgChart(mockOrgChartDto);
        deleteDto.setRootRole(mockRootRoleDto);

        // Input 2: Create new "Software Engineering" (Same Name!)
        CareerPathwayDto createDto = new CareerPathwayDto();
        createDto.setName("Software Engineering");
        createDto.setDeleted(false);
        createDto.setOrgChart(mockOrgChartDto);
        createDto.setRootRole(mockRootRoleDto);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(appMapper.toEntity(any(CareerPathwayDto.class))).thenReturn(mockEntity);
        when(careerPathwayRepository.saveAll(anyList())).thenReturn(List.of(mockEntity, mockEntity));
        when(appMapper.toDto(any(CareerPathway.class))).thenReturn(mockDto);

        // Should NOT throw BadRequestException
        assertDoesNotThrow(() -> careerPathwayService.createAndUpdateAll(List.of(deleteDto, createDto)));
    }

    // --- Delete Tests ---

    @Test
    void delete_ShouldSoftDelete() {
        when(careerPathwayRepository.findById(1L)).thenReturn(Optional.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        when(appMapper.toEntity(any(CareerPathwayDto.class))).thenAnswer(inv -> {
            CareerPathwayDto dto = inv.getArgument(0);
            CareerPathway entity = new CareerPathway();
            entity.setId(dto.getId());
            entity.setDeleted(dto.isDeleted());
            return entity;
        });

        careerPathwayService.delete(1L, USER_ID, NOW);

        verify(careerPathwayRepository).save(argThat(CareerPathway::isDeleted));
    }

    @Test
    void deleteAll_ShouldSoftDeleteList() {
        Set<Long> ids = Set.of(1L);
        when(careerPathwayRepository.findAllById(ids)).thenReturn(List.of(mockEntity));

        careerPathwayService.deleteAll(ids, USER_ID, NOW);

        verify(careerPathwayRepository).saveAll(argThat(list -> {
            List<CareerPathway> l = (List<CareerPathway>) list;
            return !l.isEmpty() && l.get(0).isDeleted();
        }));
    }
}