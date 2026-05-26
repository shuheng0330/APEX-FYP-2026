package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StaffConflictDTO {
    private UUID staffId;
    private String trainingTitle;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
}
