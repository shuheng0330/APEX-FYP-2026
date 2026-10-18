package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Everything the course player needs: terms, modules with progress, certificate and feedback. */
@Data
public class SopLearningCourseDetailDto {
    private SopLearningCourseDto course;
    private List<SopLearningModuleDto> modules = new ArrayList<>();
    /** Shown as a watermark on the material and on the certificate. */
    private String learnerName;
    /** Present once the course is completed (FR-11-03). */
    private Certificate certificate;
    private Integer feedbackRating;
    private String feedbackComment;

    @Data
    public static class Certificate {
        private String certificateNo;
        private String learnerName;
        private String courseTitle;
        private String version;
        /** The course version the certificate is for (FR-18-05). */
        private int versionNo;
        private Integer finalScore;
        private int passMark;
        private OffsetDateTime completedAt;
    }
}
