package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.EvaluationCycleDto;
import com.tbm.careerpathlearning.enums.CycleStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import com.tbm.careerpathlearning.repository.EvaluationCycleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationCycleServiceImplTest {

    @Mock
    private EvaluationCycleRepository evaluationCycleRepository;

    @Mock
    private AppMapper appMapper;

    @InjectMocks
    private EvaluationCycleServiceImpl evaluationCycleService;

    // --- Tests for openNewCycle ---
    @Test
    @DisplayName("openNewCycle - Should set status to UPCOMING if start date is in future")
    void openNewCycle_Upcoming() {
        EvaluationCycleDto dto = new EvaluationCycleDto();
        dto.setStartDate(LocalDate.now().plusDays(5));
        dto.setEndDate(LocalDate.now().plusDays(10));

        when(evaluationCycleRepository.findByStatus(CycleStatus.OPEN)).thenReturn(Optional.empty());

        evaluationCycleService.openNewCycle(dto, UUID.randomUUID());

        verify(evaluationCycleRepository).save(argThat(cycle ->
                cycle.getStatus() == CycleStatus.UPCOMING
        ));
    }

    @Test
    @DisplayName("Should throw exception when opening a cycle if an OPEN cycle already exists")
    void openNewCycle_ExistingOpenCycle_ThrowsException() {
        // Arrange
        EvaluationCycleDto dto = new EvaluationCycleDto();
        when(evaluationCycleRepository.findByStatus(CycleStatus.OPEN))
                .thenReturn(Optional.of(new EvaluationCycle()));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            evaluationCycleService.openNewCycle(dto, UUID.randomUUID());
        });

        assertEquals("An evaluation cycle is already open", exception.getMessage());
        verify(evaluationCycleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should set status to OPEN if start date is today")
    void openNewCycle_StartDateToday_SetsStatusOpen() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        EvaluationCycleDto dto = new EvaluationCycleDto();
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusDays(30));

        when(evaluationCycleRepository.findByStatus(CycleStatus.OPEN)).thenReturn(Optional.empty());

        // Act
        evaluationCycleService.openNewCycle(dto, adminId);

        // Assert
        verify(evaluationCycleRepository).save(argThat(cycle ->
                cycle.getStatus() == CycleStatus.OPEN &&
                        cycle.getOpenedAt() != null &&
                        cycle.getCreatedBy().equals(adminId)
        ));
    }

    // --- Tests for updateEvaluationCycle ---

    @Test
    @DisplayName("Should throw exception when updating a CLOSED cycle")
    void updateEvaluationCycle_ClosedCycle_ThrowsException() {
        // Arrange
        Long cycleId = 1L;
        EvaluationCycle existingCycle = new EvaluationCycle();
        existingCycle.setStatus(CycleStatus.CLOSED);

        when(evaluationCycleRepository.findById(cycleId)).thenReturn(Optional.of(existingCycle));

        // Act & Assert
        assertThrows(BadRequestException.class, () -> {
            evaluationCycleService.updateEvaluationCycle(cycleId, new EvaluationCycleDto(), UUID.randomUUID());
        });
    }

    @Test
    @DisplayName("updateEvaluationCycle - Should fail if cycle is CLOSED")
    void update_fail_closed() {
        EvaluationCycle closedCycle = new EvaluationCycle();
        closedCycle.setStatus(CycleStatus.CLOSED);

        when(evaluationCycleRepository.findById(1L)).thenReturn(Optional.of(closedCycle));

        assertThrows(BadRequestException.class, () ->
                evaluationCycleService.updateEvaluationCycle(1L, new EvaluationCycleDto(), UUID.randomUUID())
        );
    }

    @Test
    @DisplayName("Should throw exception if start date is after end date")
    void updateEvaluationCycle_InvalidDateRange_ThrowsException() {
        // Arrange
        Long cycleId = 1L;
        EvaluationCycle existingCycle = new EvaluationCycle();
        existingCycle.setStatus(CycleStatus.UPCOMING);

        EvaluationCycleDto dto = new EvaluationCycleDto();
        dto.setStartDate(LocalDate.now().plusDays(10));
        dto.setEndDate(LocalDate.now().plusDays(5)); // End before start

        when(evaluationCycleRepository.findById(cycleId)).thenReturn(Optional.of(existingCycle));

        // Act & Assert
        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            evaluationCycleService.updateEvaluationCycle(cycleId, dto, UUID.randomUUID());
        });
        assertEquals("Start date cannot be after end date", ex.getMessage());
    }

    @Test
    @DisplayName("Should change status to UPCOMING if start date is moved to the future")
    void updateEvaluationCycle_MoveToFuture_ChangesToUpcoming() {
        // Arrange
        EvaluationCycle currentCycle = new EvaluationCycle();
        currentCycle.setStatus(CycleStatus.OPEN);
        currentCycle.setOpenedAt(LocalDateTime.now());

        EvaluationCycleDto dto = new EvaluationCycleDto();
        dto.setStartDate(LocalDate.now().plusDays(5));
        dto.setEndDate(LocalDate.now().plusDays(10));

        when(evaluationCycleRepository.findById(1L)).thenReturn(Optional.of(currentCycle));
        when(evaluationCycleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(appMapper.toDto(any(EvaluationCycle.class))).thenReturn(new EvaluationCycleDto());

        evaluationCycleService.updateEvaluationCycle(1L, dto, UUID.randomUUID());

        // Assert
        assertEquals(CycleStatus.UPCOMING, currentCycle.getStatus());
        assertNull(currentCycle.getOpenedAt()); // Verify the code branch that clears openedAt
    }

    @Test
    @DisplayName("getCurrentCycle - Should return OPEN cycle if exists")
    void getCurrentCycle_OpenExists() {
        EvaluationCycle openCycle = new EvaluationCycle();
        openCycle.setStatus(CycleStatus.OPEN);

        when(evaluationCycleRepository.findFirstByStatus(CycleStatus.OPEN))
                .thenReturn(Optional.of(openCycle));
        when(appMapper.toDto(any(EvaluationCycle.class))).thenReturn(new EvaluationCycleDto());

        EvaluationCycleDto result = evaluationCycleService.getCurrentCycle();

        assertNotNull(result);
        verify(evaluationCycleRepository).findFirstByStatus(CycleStatus.OPEN);
    }

    @Test
    @DisplayName("getCurrentCycle - Should return nearest UPCOMING if no OPEN exists")
    void getCurrentCycle_UpcomingFallback() {
        when(evaluationCycleRepository.findFirstByStatus(CycleStatus.OPEN)).thenReturn(Optional.empty());
        when(evaluationCycleRepository.findFirstByStatusOrderByStartDateAsc(CycleStatus.UPCOMING))
                .thenReturn(Optional.of(new EvaluationCycle()));
        when(appMapper.toDto(any(EvaluationCycle.class))).thenReturn(new EvaluationCycleDto());

        EvaluationCycleDto result = evaluationCycleService.getCurrentCycle();

        assertNotNull(result);
        verify(evaluationCycleRepository).findFirstByStatusOrderByStartDateAsc(CycleStatus.UPCOMING);
    }

    @Test
    @DisplayName("getEvaluationCycleHistory - Should return mapped list")
    void getHistory_Success() {
        when(evaluationCycleRepository.findAllByOrderByStartDateDesc())
                .thenReturn(List.of(new EvaluationCycle(), new EvaluationCycle()));
        when(appMapper.toDto(any(EvaluationCycle.class))).thenReturn(new EvaluationCycleDto());

        List<EvaluationCycleDto> history = evaluationCycleService.getEvaluationCycleHistory();

        assertEquals(2, history.size());
    }

}