package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** What an uploaded HRDC attendance file did (FR-14-07), with the attendance as it now stands. */
@Data
public class TrainingBatchResultDto {
    /** Attendance rows read from the file. */
    private int rows;
    /** Rows matched to someone on the session. */
    private int matched;
    /** Of those, marked Attended and Absent in the file. */
    private int attended;
    private int absent;
    /** Matched, with Attended left empty, so not changed (decision B1, 28 Oct 2026). */
    private int leftBlank;
    /** Marks that changed. */
    private int changed;
    /** Rows for people not on the session, which were skipped (decision B2): "Row 5, Ali Bakar: not on this session". */
    private List<String> unmatched = new ArrayList<>();
    private TrainingAttendanceSheetDto sheet;
}
