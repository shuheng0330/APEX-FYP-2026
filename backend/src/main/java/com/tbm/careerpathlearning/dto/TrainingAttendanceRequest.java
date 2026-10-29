package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Attendance marked for some of a session's people (FR-14-01, FR-14-02). The reason is needed only when HR changes it
 * after it was submitted or after the trainer's 7 days, and is logged.
 */
@Data
public class TrainingAttendanceRequest {
    private List<Entry> entries = new ArrayList<>();
    private String reason;

    @Data
    public static class Entry {
        private UUID staffId;
        /** Null clears the mark: the person is Not Recorded again. */
        private TrainingAttendanceStatus status;
        /** 0 to 100, for someone who attended. */
        private Integer quizScore;
    }
}
