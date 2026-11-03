package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who an assignment would reach, before the trainer confirms it. */
@Data
public class SopAssignmentPreviewDto {
    private int newCount;
    private int updateCount;
    private int completedCount;
    private List<Row> staff = new ArrayList<>();

    @Data
    public static class Row {
        private UUID staffId;
        private String name;
        private String roleName;
        private String departmentName;
        /** NEW: gets the course. UPDATE: already enrolled, terms replaced. COMPLETED: already finished, left as is. */
        private String outcome;
    }
}
