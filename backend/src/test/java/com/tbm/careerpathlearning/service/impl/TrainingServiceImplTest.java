package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CascaderOptionDTO;
import com.tbm.careerpathlearning.dto.TrainingProgramDTO;
import com.tbm.careerpathlearning.enums.Status;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingServiceImplTest {

    @Mock private TrainingProgramRepository trainingProgramRepository;
    @Mock private TrainingRegistrationRepository trainingRegistrationRepository;
    @Mock private TrainingTargetRoleRepository trainingTargetRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CompetencyRepository competencyRepository;
    @Mock private OrgChartRepository orgChartRepository;
    @Mock private AppMapper appMapper;

    @InjectMocks
    private TrainingServiceImpl trainingService;
    private TrainingProgram sampleProgram;
    private TrainingProgramDTO sampleDto;
    private UUID userId;
    private final UUID USER_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sampleProgram = new TrainingProgram();
        sampleProgram.setTrainingId(1L);
        sampleProgram.setTitle("Advanced Unit Testing");

        userId = UUID.randomUUID();
        sampleDto = new TrainingProgramDTO();
        sampleDto.setTrainingId(1L);
        sampleDto.setTitle("Advanced Unit Testing");
        sampleDto.setCompetencyIds(List.of(101L));
        sampleDto.setDepartmentIds(List.of(201L));
        sampleDto.setRoleIds(List.of(301L));
        sampleDto.setCapacity(20);
    }

    @Test
    @DisplayName("Create Training - Success: Should save program with relations")
    void createTrainingProgram_Success() {
        // Arrange
        TrainingProgram mockEntity = new TrainingProgram();
        mockEntity.setTrainingId(1L);

        when(appMapper.toEntity(any(TrainingProgramDTO.class))).thenReturn(mockEntity);
        when(competencyRepository.findAllById(any())).thenReturn(List.of(new Competency()));
        when(orgChartRepository.findAllById(any())).thenReturn(List.of(new OrgChart()));
        when(roleRepository.findById(301L)).thenReturn(Optional.of(new Role()));
        when(trainingProgramRepository.save(any(TrainingProgram.class))).thenReturn(mockEntity);
        when(appMapper.toDto(any(TrainingProgram.class))).thenReturn(sampleDto);

        // Act
        TrainingProgramDTO result = trainingService.createTrainingProgram(sampleDto, userId);

        // Assert
        assertNotNull(result);
        assertEquals("Advanced Unit Testing", result.getTitle());
        verify(trainingProgramRepository).save(argThat(p ->
                p.getStatus() == Status.UPCOMING && !p.getIsDeleted()
        ));
        verify(trainingTargetRoleRepository, times(1)).save(any(TrainingTargetRole.class));
    }

    @Test
    @DisplayName("Update Training - Should populate registered counts correctly")
    void updateTrainingProgram_PopulatesCounts() {
        // Arrange
        Long trainingId = 1L;
        TrainingProgram existing = new TrainingProgram();
        existing.setTrainingId(trainingId);

        when(trainingProgramRepository.findById(trainingId)).thenReturn(Optional.of(existing));
        when(trainingProgramRepository.save(any())).thenReturn(existing);
        when(appMapper.toDto(any(TrainingProgram.class))).thenReturn(sampleDto);
        // Mocking populateRegisteredCounts behavior via registration repo
        List<Object[]> mockCountResult = new ArrayList<>();
        mockCountResult.add(new Object[]{1L, 5L}); // 5 people registered
        when(trainingRegistrationRepository.countRegistrationsByTrainingIds(any())).thenReturn(mockCountResult);

        // Act
        TrainingProgramDTO result = trainingService.updateTrainingProgram(trainingId, sampleDto, userId);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getRegisteredCount());
    }

    @Test
    @DisplayName("Delete Training - Should perform soft delete")
    void deleteTrainingProgram_SoftDelete() {
        // Arrange
        Long id = 1L;
        TrainingProgram training = new TrainingProgram();
        training.setIsDeleted(false);
        when(trainingProgramRepository.findById(id)).thenReturn(Optional.of(training));

        // Act
        trainingService.deleteTrainingProgramById(id, userId);

        // Assert
        assertTrue(training.getIsDeleted());
        verify(trainingProgramRepository).save(training);
    }

    @Test
    @DisplayName("getAllTrainingPrograms - Should return list and populate counts")
    void getAllTrainingPrograms_Success() {
        // Targets 0% method: getAllTrainingPrograms
        when(trainingProgramRepository.findByIsDeletedFalse()).thenReturn(List.of(sampleProgram));
        when(appMapper.toDto(any(TrainingProgram.class))).thenReturn(sampleDto);

        // FIX: Cast the list explicitly to List<Object[]> so Mockito can match the return type
        List<Object[]> mockCounts = new ArrayList<>();
        mockCounts.add(new Object[]{1L, 5L});

        when(trainingRegistrationRepository.countRegistrationsByTrainingIds(anyList()))
                .thenReturn(mockCounts);

        List<TrainingProgramDTO> results = trainingService.getAllTrainingPrograms();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(5, results.get(0).getRegisteredCount());
    }

    @Test
    @DisplayName("getTrainingPrograms (Pageable) - Should return paged results")
    void getTrainingPrograms_Paged_Success() {
        // Targets 0% method: getTrainingPrograms(Pageable)
        Pageable pageable = PageRequest.of(0, 10);
        Page<TrainingProgram> page = new PageImpl<>(List.of(sampleProgram));

        when(trainingProgramRepository.findByIsDeletedFalse(pageable)).thenReturn(page);
        when(appMapper.toDto(any(TrainingProgram.class))).thenReturn(sampleDto);
        when(trainingRegistrationRepository.countRegistrationsByTrainingIds(anyList()))
                .thenReturn(Collections.emptyList());

        Page<TrainingProgramDTO> resultPage = trainingService.getTrainingPrograms(pageable);

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getContent().size());
    }

    @Test
    @DisplayName("getTrainingProgramById - Should return DTO or null")
    void getTrainingProgramById_Success() {
        // Targets 0% method: getTrainingProgramById
        when(trainingProgramRepository.findByTrainingIdAndIsDeletedFalse(1L))
                .thenReturn(Optional.of(sampleProgram));
        when(appMapper.toDto(sampleProgram)).thenReturn(sampleDto);

        TrainingProgramDTO result = trainingService.getTrainingProgramById(1L);

        assertNotNull(result);
        assertEquals("Advanced Unit Testing", result.getTitle());
    }

    @Test
    @DisplayName("getRoleCascaderOptions - Should group roles by OrgChart")
    void getRoleCascaderOptions_Success() {
        // Targets 0% method: getRoleCascaderOptions
        OrgChart dept = new OrgChart();
        dept.setId(10L);
        dept.setName("IT");

        Role role = new Role();
        role.setId(5L);
        role.setName("Developer");
        role.setOrgChart(dept);

        when(roleRepository.findAllVisibleAndNotDeletedWithOrgChartTypeD()).thenReturn(List.of(role));

        List<CascaderOptionDTO> options = trainingService.getRoleCascaderOptions();

        assertFalse(options.isEmpty());
        assertEquals("IT", options.get(0).getLabel());
        assertEquals(1, options.get(0).getChildren().size());
        assertEquals("Developer", options.get(0).getChildren().get(0).getLabel());
    }

    @Test
    @DisplayName("getTrainingProgramsByStaffId - Should return staff specific trainings")
    void getTrainingProgramsByStaffId_Success() {
        // Targets 0% method: getTrainingProgramsByStaffId
        when(trainingRegistrationRepository.findActiveTrainingsByStaffId(USER_UUID))
                .thenReturn(List.of(sampleProgram));
        when(appMapper.toDto(any(TrainingProgram.class))).thenReturn(sampleDto);
        when(trainingRegistrationRepository.countRegistrationsByTrainingIds(anyList()))
                .thenReturn(Collections.emptyList());

        List<TrainingProgramDTO> results = trainingService.getTrainingProgramsByStaffId(USER_UUID);

        assertNotNull(results);
        assertEquals(1, results.size());
    }
}
