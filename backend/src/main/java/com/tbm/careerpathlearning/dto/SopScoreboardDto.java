package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** The Training Scoreboard: every enrolment on the courses the user may see, with filter options (FR-10-03). */
@Data
public class SopScoreboardDto {
    private List<Course> courses = new ArrayList<>();
    private List<String> departments = new ArrayList<>();
    private List<SopScoreboardRecordDto> records = new ArrayList<>();
    /** When these figures were worked out; the page refreshes them while it is open. */
    private OffsetDateTime generatedAt;

    @Data
    public static class Course {
        private Long id;
        private String title;
        private boolean archived;
    }
}
