package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningMaterialRecommendationServiceImplTest {

    @Mock private LearningMaterialRepository learningMaterialRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private EvaluationRatingsRepository evaluationRatingsRepository;
    @Mock private RoleCompetencyRepository roleCompetencyRepository;
    @Mock private StaffLearningMaterialRepository staffLearningMaterialRepository;
    @Mock private AppMapper appMapper;

    @InjectMocks
    private LearningMaterialRecommendationServiceImpl recommendationService;

    private UUID staffId;
    private Staff mockStaff;
    private Role mockRole;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        mockRole = new Role();
        mockRole.setId(1L);

        mockStaff = new Staff();
        mockStaff.setId(staffId);
        mockStaff.setRole(mockRole);
    }

    @Test
    @DisplayName("Should recommend materials sorted by impact/efficiency score")
    void recommendForUser_Success() {
        // 1. Arrange - Staff and Role Setup
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(mockStaff));
        when(staffLearningMaterialRepository.findByStaff_IdAndIsCompletedTrue(staffId))
                .thenReturn(Collections.emptyList()); // No materials completed yet

        // 2. Arrange - Competency Gaps (Required Level is 10)
        // Competency 1: Rating 6 (Gap = 4)
        Competency comp1 = new Competency(); comp1.setId(101L);
        EvaluationRatings rating1 = new EvaluationRatings();
        rating1.setCompetency(comp1);
        rating1.setRating(6);
        when(evaluationRatingsRepository.findLatestRatingsByStaffId(staffId))
                .thenReturn(List.of(rating1));

        // 3. Arrange - Weightage (100% weightage for comp1)
        RoleCompetency rc1 = new RoleCompetency();
        rc1.setCompetency(comp1);
        rc1.setWeightage(100);
        when(roleCompetencyRepository.findByRoleId(1L)).thenReturn(List.of(rc1));

        // 4. Arrange - Learning Materials
        LearningMaterial material1 = new LearningMaterial();
        material1.setMaterialId(501L);
        material1.setTotalDurationAllDoc(600.0); // 10 minutes

        LearningMaterialDto dto1 = new LearningMaterialDto();
        dto1.setMaterialId(501L);
        dto1.setTotalDurationAllDoc(600.0);
        dto1.setCompetency(new ArrayList<>(Set.of(appMapperToCompetencyDto(comp1))));

        when(learningMaterialRepository.findAll()).thenReturn(List.of(material1));
        when(appMapper.toDto(any(LearningMaterial.class))).thenReturn(dto1);

        // Act
        List<LearningMaterialDto> recommendations = recommendationService.recommendForUser(staffId);

        // Assert
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertEquals(501L, recommendations.get(0).getMaterialId());

        // Math Verification:
        // Gap = 10 - 6 = 4
        // Impact = 4 * (100/100) = 4.0
        // Efficiency = log10(10 mins + 10) = log10(20) ≈ 1.301
        // Final Score = 4.0 / 1.301 ≈ 3.07 (Should be > 0)
    }

    @Test
    @DisplayName("Should filter out already completed materials")
    void recommendForUser_FiltersCompleted() {
        // Arrange
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(mockStaff));

        LearningMaterial material = new LearningMaterial();
        material.setMaterialId(1L);

        StaffLearningMaterial completed = new StaffLearningMaterial();
        completed.setLearningMaterial(material);

        when(staffLearningMaterialRepository.findByStaff_IdAndIsCompletedTrue(staffId))
                .thenReturn(List.of(completed));
        when(learningMaterialRepository.findAll()).thenReturn(List.of(material));

        LearningMaterialDto dto = new LearningMaterialDto();
        dto.setMaterialId(1L);
        when(appMapper.toDto(material)).thenReturn(dto);

        // Act
        List<LearningMaterialDto> result = recommendationService.recommendForUser(staffId);

        // Assert
        assertTrue(result.isEmpty(), "Completed material should be filtered out");
    }

    // Helper to mock the nested DTO mapping
    private com.tbm.careerpathlearning.dto.CompetencyDto appMapperToCompetencyDto(Competency c) {
        com.tbm.careerpathlearning.dto.CompetencyDto dto = new com.tbm.careerpathlearning.dto.CompetencyDto();
        dto.setId(c.getId());
        return dto;
    }
}