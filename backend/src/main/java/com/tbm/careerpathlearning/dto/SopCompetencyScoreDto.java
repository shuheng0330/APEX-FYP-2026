package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A completed SOP course as a competency score, for the Staff Appraisal module to read
 * (FR-10-06). The final score is the average of the module quiz scores that passed.
 */
@Data
public class SopCompetencyScoreDto {
    private UUID staffId;
    private String staffName;
    private Long sopDocumentId;
    private String courseTitle;
    private String version;
    /** The SOP's department tag, e.g. "Front Office". */
    private String category;
    private int finalScore;
    private int passMark;
    private OffsetDateTime completedAt;
    private String certificateNo;
}
