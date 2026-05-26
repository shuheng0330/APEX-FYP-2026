package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CheckInResponseDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingAttendanceDto;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingAttendanceServiceImplTest {

    @Mock private TrainingAttendanceRepository trainingAttendanceRepository;
    @Mock private TrainingProgramRepository trainingProgramRepository;
    @Mock private StaffRepository staffRepository;
    @Mock AppMapper appMapper;

    @InjectMocks
    private TrainingAttendanceServiceImpl attendanceService;

    private TrainingProgram mockTraining;
    private TrainingAttendanceDto attendanceDto;
    private final double CENTER_LAT = 3.1390; // Kuala Lumpur example
    private final double CENTER_LON = 101.6869;

    @BeforeEach
    void setUp() {
        mockTraining = new TrainingProgram();
        mockTraining.setTrainingId(1L);
        mockTraining.setLatitude(CENTER_LAT);
        mockTraining.setLongitude(CENTER_LON);

        attendanceDto = new TrainingAttendanceDto();
        attendanceDto.setTrainingId(1L);
        attendanceDto.setStaffId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Check-in - Success: User is inside 100m radius")
    void checkIn_InsideGeofence_Success() {
        // Arrange: Use coordinates very close to the center (~11 meters away)
        attendanceDto.setCheckInLatitude(3.1391);
        attendanceDto.setCheckInLongitude(101.6869);

        when(trainingProgramRepository.findById(1L)).thenReturn(Optional.of(mockTraining));
        when(staffRepository.findById(any())).thenReturn(Optional.of(new Staff()));

        // Act
        CheckInResponseDto response = attendanceService.checkIn(attendanceDto);

        // Assert
        assertTrue(response.isSuccess());
        assertTrue(response.getDistance() < 100);
        assertEquals("Check-in successful!", response.getMessage());
        verify(trainingAttendanceRepository, times(1)).save(any(TrainingAttendance.class));
    }

    @Test
    @DisplayName("Check-in - Failure: User is outside 100m radius")
    void checkIn_OutsideGeofence_Failure() {
        // Arrange: Use coordinates far away
        attendanceDto.setCheckInLatitude(3.2000);
        attendanceDto.setCheckInLongitude(101.7000);

        when(trainingProgramRepository.findById(1L)).thenReturn(Optional.of(mockTraining));

        // Act
        CheckInResponseDto response = attendanceService.checkIn(attendanceDto);

        // Assert
        assertFalse(response.isSuccess());
        assertTrue(response.getDistance() > 100);
        assertTrue(response.getMessage().contains("outside the training location"));
        // Should NOT save to DB if outside geofence
        verify(trainingAttendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Check-in - Error: Training program not found")
    void checkIn_TrainingNotFound_ThrowsException() {
        when(trainingProgramRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> attendanceService.checkIn(attendanceDto));
    }

    @Test
    @DisplayName("Get staff attendance list - Success")
    void getStaffAttendanceListByTrainingId_Success() {
        // Arrange
        Long trainingId = 1L;
        Staff staff = new Staff();
        StaffDto staffDto = new StaffDto();

        when(trainingAttendanceRepository.findStaffAttendanceByTrainingProgramId(trainingId))
                .thenReturn(Collections.singletonList(staff));
        when(appMapper.toDto(any(Staff.class))).thenReturn(staffDto);

        // Act
        List<StaffDto> result = attendanceService.getStaffAttendanceListByTrainingId(trainingId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(trainingAttendanceRepository).findStaffAttendanceByTrainingProgramId(trainingId);
    }

    @Test
    @DisplayName("Find training attendance by training ID - Success")
    void findTrainingAttendanceByTrainingId_Success() {
        // Arrange
        Long trainingId = 1L;
        TrainingAttendance attendance = new TrainingAttendance();
        TrainingAttendanceDto dto = new TrainingAttendanceDto();

        when(trainingAttendanceRepository.findTrainingAttendanceByTrainingProgram_TrainingIdAndTrainingProgram_IsDeletedFalse(trainingId))
                .thenReturn(Collections.singletonList(attendance));
        when(appMapper.toDto(any(TrainingAttendance.class))).thenReturn(dto);

        // Act
        List<TrainingAttendanceDto> result = attendanceService.findTrainingAttendanceByTrainingId(trainingId);

        // Assert
        assertFalse(result.isEmpty());
        verify(trainingAttendanceRepository).findTrainingAttendanceByTrainingProgram_TrainingIdAndTrainingProgram_IsDeletedFalse(trainingId);
    }

    @Test
    @DisplayName("Get attended training by staff ID - Success")
    void getAttendedTrainingByStaffId_Success() {
        // Arrange
        UUID staffId = UUID.randomUUID();
        when(trainingAttendanceRepository.findTrainingAttendanceIdByStaffId(staffId))
                .thenReturn(List.of(101L, 102L));

        // Act
        List<Long> result = attendanceService.getAttendedTrainingByStaffId(staffId);

        // Assert
        assertEquals(2, result.size());
        assertEquals(101L, result.get(0));
    }

    @Test
    @DisplayName("Check-in - Staff Not Found Throws Exception")
    void checkIn_StaffNotFound_ThrowsException() {
        // To cover the second branch in mapToEntity
        attendanceDto.setCheckInLatitude(3.1391);
        attendanceDto.setCheckInLongitude(101.6869);

        when(trainingProgramRepository.findById(anyLong())).thenReturn(Optional.of(mockTraining));
        when(staffRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> attendanceService.checkIn(attendanceDto));
    }
}