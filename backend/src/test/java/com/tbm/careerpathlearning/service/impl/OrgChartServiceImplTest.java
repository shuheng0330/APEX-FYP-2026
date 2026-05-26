package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
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
class OrgChartServiceImplTest {

    @Mock
    private OrgChartRepository orgChartRepository;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private OrgChartServiceImpl orgChartService;

    private OrgChart mockOrgChart;
    private OrgChartDto mockOrgChartDto;
    private UUID userUUID;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();
        now = OffsetDateTime.now();

        mockOrgChart = new OrgChart();
        mockOrgChart.setId(1L);
        mockOrgChart.setName("IT Department");
        mockOrgChart.setType(OrgChartType.D);
        mockOrgChart.setDeleted(false);

        mockOrgChartDto = new OrgChartDto();
        mockOrgChartDto.setId(1L);
        mockOrgChartDto.setName("IT Department");
        mockOrgChartDto.setType(OrgChartType.D);
        mockOrgChartDto.setDeleted(false);
        mockOrgChartDto.setCreatedBy(userUUID);
        mockOrgChartDto.setUpdatedBy(userUUID);
        mockOrgChartDto.setCreatedAt(now);
        mockOrgChartDto.setUpdatedAt(now);

        // Lenient stubbing for message source to prevent unnecessary failures in validation tests
        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error Message");
    }

    // --- Find Tests ---

    @Test
    void findAllByIsDeletedIsFalse_ShouldReturnList() {
        when(orgChartRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrgChart));
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto);

        List<OrgChartDto> result = orgChartService.findAllByIsDeletedIsFalse();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("IT Department", result.get(0).getName());
    }

    @Test
    void getByById_ShouldReturnDto_WhenExists() {
        when(orgChartRepository.findById(1L)).thenReturn(Optional.of(mockOrgChart));
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto);

        OrgChartDto result = orgChartService.getByById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getByById_ShouldThrowException_WhenNotFound() {
        when(orgChartRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DataAccessException.class, () -> orgChartService.getByById(1L));
    }

    @Test
    void getDepartments_ShouldReturnList() {
        when(orgChartRepository.findAllByOrgChartTypeD()).thenReturn(List.of(mockOrgChart));
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto);

        List<OrgChartDto> result = orgChartService.getDepartments();

        assertFalse(result.isEmpty());
        assertEquals(OrgChartType.D, result.get(0).getType());
    }

    // --- Create Tests ---

    @Test
    void create_ShouldSave_WhenValidAndUnique() {
        when(orgChartRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // No existing names
        when(appMapper.toEntity(mockOrgChartDto)).thenReturn(mockOrgChart);
        when(orgChartRepository.save(mockOrgChart)).thenReturn(mockOrgChart);
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto);

        OrgChartDto result = orgChartService.create(mockOrgChartDto);

        assertNotNull(result);
        verify(orgChartRepository).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenNameIsDuplicate() {
        // Arrange: Existing org chart with same name
        when(orgChartRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockOrgChart));

        // Act & Assert
        assertThrows(BadRequestException.class, () -> orgChartService.create(mockOrgChartDto));
        verify(orgChartRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenValidationFails_NullName() {
        mockOrgChartDto.setName(null);
        assertThrows(BadRequestException.class, () -> orgChartService.create(mockOrgChartDto));
    }

    @Test
    void create_ShouldThrowException_WhenValidationFails_NameTooLong() {
        mockOrgChartDto.setName("A".repeat(256)); // 256 chars
        assertThrows(BadRequestException.class, () -> orgChartService.create(mockOrgChartDto));
    }

    // --- Update Tests ---

    @Test
    void update_ShouldSave_WhenValid() {
        // Mock getById inside update method
        when(orgChartRepository.findById(1L)).thenReturn(Optional.of(mockOrgChart));
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto); // First toDto call inside getByById

        when(appMapper.toEntity(mockOrgChartDto)).thenReturn(mockOrgChart);
        when(orgChartRepository.save(mockOrgChart)).thenReturn(mockOrgChart);
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto); // Second toDto call for return

        OrgChartDto result = orgChartService.update(1L, mockOrgChartDto);

        assertNotNull(result);
        verify(orgChartRepository).save(any());
    }

    @Test
    void update_ShouldThrowException_WhenIdIsNull() {
        assertThrows(BadRequestException.class, () -> orgChartService.update(null, mockOrgChartDto));
    }

    @Test
    void update_ShouldThrowException_WhenNameIsBlank() {
        mockOrgChartDto.setName(null);
        assertThrows(BadRequestException.class, () -> orgChartService.update(1L, mockOrgChartDto));
    }

    // --- Bulk Create/Update Tests ---

    @Test
    void createAndUpdateAll_ShouldHandleBothOperations() {
        // Arrange
        OrgChartDto newDto = new OrgChartDto();
        newDto.setName("New Dept");
        newDto.setType(OrgChartType.D);
        newDto.setCreatedBy(userUUID);
        newDto.setUpdatedBy(userUUID);
        newDto.setCreatedAt(now);
        newDto.setUpdatedAt(now);

        List<OrgChartDto> dtos = List.of(mockOrgChartDto, newDto); // One existing (ID 1), one new (ID null)

        when(orgChartRepository.findAllById(any())).thenReturn(List.of(mockOrgChart)); // Find existing
        when(orgChartRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // Check unique names
        when(appMapper.toEntity(newDto)).thenReturn(new OrgChart());

        when(orgChartRepository.saveAll(anyList())).thenReturn(List.of(mockOrgChart, new OrgChart()));
        when(appMapper.toDto(any(OrgChart.class))).thenReturn(mockOrgChartDto);

        // Act
        List<OrgChartDto> result = orgChartService.createAndUpdateAll(dtos);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orgChartRepository).saveAll(anyList());
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenListEmpty() {
        assertThrows(BadRequestException.class, () -> orgChartService.createAndUpdateAll(Collections.emptyList()));
    }

    // --- Delete Tests ---

    @Test
    void deleteById_ShouldMarkAsDeleted() {
        // Mock finding the entity
        when(orgChartRepository.findById(1L)).thenReturn(Optional.of(mockOrgChart));
        when(appMapper.toDto(mockOrgChart)).thenReturn(mockOrgChartDto);
        when(appMapper.toEntity(mockOrgChartDto)).thenReturn(mockOrgChart);

        orgChartService.deleteById(1L, userUUID, now);

        // Verify save was called on an entity marked as deleted
        verify(orgChartRepository).save(argThat(entity -> entity.isDeleted()));
    }

    @Test
    void deleteAllByIdIn_ShouldMarkAllAsDeleted() {
        Set<Long> ids = Set.of(1L);
        when(orgChartRepository.findAllById(ids)).thenReturn(List.of(mockOrgChart));

        orgChartService.deleteAllByIdIn(ids, userUUID, now);

        verify(orgChartRepository).saveAll(argThat(iterable -> {
            OrgChart item = iterable.iterator().next();
            return item.isDeleted() && item.getId().equals(1L);
        }));
    }
}