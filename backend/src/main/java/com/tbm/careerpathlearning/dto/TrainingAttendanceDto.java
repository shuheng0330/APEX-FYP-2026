package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TrainingAttendanceDto {

    private Long attendanceId;

    private Long trainingId;

    private UUID staffId;

    private LocalDateTime checkInTime;

    private Double checkInLatitude;

    private Double checkInLongitude;

    private Boolean withinGeofence;
}
