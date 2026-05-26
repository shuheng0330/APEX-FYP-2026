package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class TrainingAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    @ManyToOne
    @JoinColumn(name = "training_id")
    private TrainingProgram trainingProgram;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    private LocalDateTime checkInTime;

    @Column(name = "check_in_latitude")
    private Double checkInLatitude;

    @Column(name = "check_in_longitude")
    private Double checkInLongitude;

    private Boolean withinGeofence;
}


