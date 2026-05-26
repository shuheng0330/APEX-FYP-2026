package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.EvaluationDTO;
import com.tbm.careerpathlearning.dto.RatingDTO;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private CompetencyRepository competencyRepository;
    @Mock private EvaluationCycleRepository evaluationCycleRepository;
    @Mock private RoleCompetencyRepository roleCompetencyRepository;
    @Mock private StaffService staffService;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private EvaluationServiceImpl evaluationService;

    private Staff mockStaff;
    private Role mockRole;
    private EvaluationDTO sampleDto;

    @BeforeEach
    void setUp() {
        mockRole = new Role();
        mockRole.setId(10L);

        mockStaff = new Staff();
        mockStaff.setId(UUID.randomUUID());
        mockStaff.setName("John Doe");
        mockStaff.setRole(mockRole);

        sampleDto = new EvaluationDTO();
        sampleDto.setStaffId(mockStaff.getId());
        sampleDto.setComment("Great performance");

        RatingDTO r1 = new RatingDTO();
        r1.setCompId(1L); r1.setRating(4);
        sampleDto.setRatings(List.of(r1));
    }

    @Test
    @DisplayName("Should throw BadRequestException when rating exceeds 10")
    void createEvaluation_RatingOutOfBounds_ThrowsException() {
        // Arrange
        when(staffRepository.findById(any())).thenReturn(Optional.of(mockStaff));

        RatingDTO invalidRating = new RatingDTO();
        invalidRating.setCompId(1L);
        invalidRating.setRating(11); // Out of bounds (Max 10)

        sampleDto.setRatings(List.of(invalidRating));

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Rating must be between 1 and 10");

        // Act & Assert
        assertThrows(BadRequestException.class, () -> evaluationService.createEvaluation(sampleDto));
    }

    @Test
    @DisplayName("Should throw DataAccessException when staff is not found")
    void createEvaluation_StaffNotFound_ThrowsException() {
        when(staffRepository.findById(any())).thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Staff not found");

        assertThrows(DataAccessException.class, () -> evaluationService.createEvaluation(sampleDto));
    }

    @Test
    @DisplayName("Should throw BadRequestException when no role competencies are configured")
    void createEvaluation_NoRoleCompConfigured_ThrowsException() {
        when(staffRepository.findById(any())).thenReturn(Optional.of(mockStaff));
        when(roleCompetencyRepository.findByRoleId(anyLong())).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Not configured");

        assertThrows(BadRequestException.class, () -> evaluationService.createEvaluation(sampleDto));
    }

    @Test
    void createEvaluation_emptyRatings_shouldThrowException() {
        Staff staff = new Staff();
        Role role = new Role();
        role.setId(1L);
        staff.setRole(role);

        // 1. Create the Competency dependency
        Competency competency = new Competency();
        competency.setId(10L);

        // 2. Link it to the RoleCompetency
        RoleCompetency roleCompetency = new RoleCompetency();
        roleCompetency.setCompetency(competency); // Fixes the NPE

        EvaluationDTO dto = new EvaluationDTO();
        dto.setStaffId(UUID.randomUUID());
        dto.setRatings(List.of()); // This is what you actually want to test

        when(staffRepository.findById(any()))
                .thenReturn(Optional.of(staff));

        // 3. Return the properly linked object
        when(roleCompetencyRepository.findByRoleId(any()))
                .thenReturn(List.of(roleCompetency));

        when(messageSource.getMessage(any(), any(), any()))
                .thenReturn("Ratings empty");

        assertThrows(BadRequestException.class,
                () -> evaluationService.createEvaluation(dto));
    }

    @Test
    @DisplayName("Should correctly calculate weighted overall score")
    void createEvaluation_Success_CalculatesCorrectScore() {
        // 1. Setup Staff with Role
        Role mockRole = new Role();
        mockRole.setId(10L);
        mockStaff.setRole(mockRole);
        when(staffRepository.findById(any())).thenReturn(Optional.of(mockStaff));

        // 2. Mock Active Evaluation Cycle with Dates (FIXES THE NPE)
        EvaluationCycle mockCycle = new EvaluationCycle();
        mockCycle.setId(1L);
        mockCycle.setStartDate(LocalDate.now().minusMonths(1)); // Provide a start date
        mockCycle.setEndDate(LocalDate.now().plusMonths(1));    // PROVIDE THE END DATE

        when(evaluationCycleRepository.findActiveCycle()).thenReturn(Optional.of(mockCycle));

        // 3. Mock Competency and Weightage
        Competency comp = new Competency();
        comp.setId(1L);
        comp.setName("Technical");

        RoleCompetency rc = new RoleCompetency();
        rc.setCompetency(comp);
        rc.setWeightage(50);

        when(roleCompetencyRepository.findByRoleId(10L)).thenReturn(List.of(rc));
        when(competencyRepository.findById(1L)).thenReturn(Optional.of(comp));

        // 4. Mock the Save operation
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(i -> {
            Evaluation eval = i.getArgument(0);
            eval.setEvaluationId(1L); // Ensure saved object has an ID for mapping
            return eval;
        });

        // 5. Act
        EvaluationDTO result = evaluationService.createEvaluation(sampleDto);

        // 6. Assert
        assertNotNull(result);
        assertEquals(40.0, result.getOverallScore());
    }

    @Test
    @DisplayName("Should fetch evaluations for downline staff")
    void getAllDownLineEvaluation_ReturnsList() {
        // Arrange
        UUID managerId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(staffService.getAllDownlineStaffIds(managerId)).thenReturn(Set.of(subId));

        Evaluation eval = new Evaluation();
        eval.setStaff(mockStaff);
        eval.setRatings(new ArrayList<>());

        when(evaluationRepository.findAllByStaffIdIn(anySet())).thenReturn(List.of(eval));

        // Act
        List<EvaluationDTO> results = evaluationService.getAllDownLineEvaluation(managerId);

        // Assert
        assertEquals(1, results.size());
        verify(evaluationRepository).findAllByStaffIdIn(Set.of(subId));
    }

    @Test
    void getAllEvaluations_shouldReturnList() {
        Evaluation evaluation = new Evaluation();
        evaluation.setStaff(new Staff());
        evaluation.setRatings(List.of());

        when(evaluationRepository.findAllWithRatings())
                .thenReturn(List.of(evaluation));

        List<EvaluationDTO> result = evaluationService.getAllEvaluations();

        assertEquals(1, result.size());
    }

    @Test
    void getAllEvaluationsByStaffId_shouldReturnList() {
        UUID staffId = UUID.randomUUID();

        Evaluation evaluation = new Evaluation();
        evaluation.setStaff(new Staff());
        evaluation.setRatings(List.of());

        when(evaluationRepository.findAllByStaffWithRatings(staffId))
                .thenReturn(List.of(evaluation));

        List<EvaluationDTO> result =
                evaluationService.getAllEvaluationsByStaffId(staffId);

        assertEquals(1, result.size());
    }
}