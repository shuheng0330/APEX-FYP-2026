package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** One staff member's progress through one course, module by module, with every quiz attempt (FR-10-02). */
@Data
public class SopEnrollmentProgressDto {
    private SopScoreboardRecordDto record;
    private List<Module> modules = new ArrayList<>();

    @Data
    public static class Module {
        private Long moduleId;
        private int moduleOrder;
        private String title;
        /** LOCKED, AVAILABLE, RETRAINING, PASSED or UNAVAILABLE, as the learner sees it. */
        private String state;
        private Integer passedScore;
        private List<Attempt> attempts = new ArrayList<>();
    }

    @Data
    public static class Attempt {
        private int score;
        private int correct;
        private int total;
        private boolean passed;
        private OffsetDateTime attemptedAt;
    }
}
