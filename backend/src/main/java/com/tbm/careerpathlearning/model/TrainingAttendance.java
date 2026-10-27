package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A staff member's attendance at a session, recorded by the trainer (FR-14). The old GPS self check-in is retired
 * (decision B, 27 Oct 2026): a phone's position can be faked, so its position columns are no longer used.
 */
@Getter
@Setter
@Entity
public class TrainingAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    @Column(name = "training_id", nullable = false)
    private Long trainingId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;
}
