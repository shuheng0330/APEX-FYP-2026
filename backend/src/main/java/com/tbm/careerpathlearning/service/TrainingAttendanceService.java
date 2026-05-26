package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CheckInResponseDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingAttendanceDto;

import java.util.List;
import java.util.UUID;

public interface TrainingAttendanceService {
    CheckInResponseDto checkIn(TrainingAttendanceDto dto);

    List<StaffDto> getStaffAttendanceListByTrainingId(Long trainingId);

    List<Long> getAttendedTrainingByStaffId(UUID staffId);

    List<TrainingAttendanceDto> findTrainingAttendanceByTrainingId(Long trainingId);
}
