package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.TrainingAttendanceMethod;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
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

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ATTENDED'")
    @Column(name = "status", nullable = false, length = 20)
    private TrainingAttendanceStatus status = TrainingAttendanceStatus.ATTENDED;

    /** A quiz score from the session, 0 to 100, recorded with attendance (FR-14-02). */
    @Column(name = "quiz_score")
    private Integer quizScore;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MANUAL'")
    @Column(name = "method", nullable = false, length = 20)
    private TrainingAttendanceMethod method = TrainingAttendanceMethod.MANUAL;

    /** Who recorded it and when (FR-14-03). */
    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    /** Attended without registering, added by the trainer (decision A, 28 Oct 2026). */
    @Column(name = "walk_in", nullable = false, columnDefinition = "boolean default false not null")
    private boolean walkIn;
}
