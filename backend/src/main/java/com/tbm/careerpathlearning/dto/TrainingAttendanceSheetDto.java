package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A session's attendance as the trainer and HR record it (FR-14): everyone registered and any walk-ins, each Attended,
 * Absent or Not Recorded, with the evidence attached.
 */
@Data
public class TrainingAttendanceSheetDto {
    private Long sessionId;
    /** The viewer may record now without giving a reason: the trainer or HR, from the start until 7 days after. */
    private boolean canRecord;
    /** Only HR can change it now, giving a reason that is logged: it was submitted, or the 7 days have passed. */
    private boolean hrOnly;
    /** The viewer is HR and may make that change. */
    private boolean canChangeWithReason;
    /** When recording opens (the session's start) and when the trainer's 7 days end. */
    private LocalDateTime opensAt;
    private LocalDateTime trainerUntil;
    private boolean submitted;
    private LocalDateTime submittedAt;
    private String submittedByName;
    /** Everyone is marked, so the trainer may submit (FR-14-09). */
    private boolean canSubmit;
    private int attended;
    private int absent;
    private int notRecorded;
    private List<Row> rows = new ArrayList<>();
    private List<Evidence> evidence = new ArrayList<>();

    @Data
    public static class Row {
        private UUID staffId;
        private String name;
        private String roleName;
        private String departmentName;
        /** ATTENDED, ABSENT or NOT_RECORDED (FR-14-04). */
        private String status;
        private Integer quizScore;
        private boolean walkIn;
        /** MANUAL or BATCH (FR-14-03). */
        private String method;
        private String recordedByName;
        private LocalDateTime recordedAt;
    }

    @Data
    public static class Evidence {
        private Long id;
        private String fileName;
        private String contentType;
        private long sizeBytes;
        private String uploadedByName;
        private LocalDateTime uploadedAt;
    }
}
