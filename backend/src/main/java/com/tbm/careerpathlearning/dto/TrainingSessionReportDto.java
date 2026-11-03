package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionTiming;
import com.tbm.careerpathlearning.enums.TrainingType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** A session's report (FR-12-07): what it was, its numbers, and everyone on its attendance list. */
@Data
public class TrainingSessionReportDto {
    private Long sessionId;
    private String title;
    private TrainingSessionKind kind;
    private TrainingDelivery delivery;
    private TrainingType trainingType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private int durationMinutes;
    private String venue;
    private String trainerName;
    private boolean externalTrainer;
    private boolean mandatory;
    private TrainingSessionStatus status;
    private TrainingSessionTiming timing;
    private String cancelReason;

    private int capacity;
    private int registered;
    private int waitlisted;
    private int walkIns;
    private int attended;
    private int absent;
    private int notRecorded;
    /** Attended, as a share of everyone on the attendance list; null when nobody is on it. */
    private Integer attendanceRate;
    /** Everyone attended, times the session's scheduled length (FR-17-04). */
    private double trainingHours;
    /** Of the quiz scores recorded; null when there are none. */
    private Integer averageQuizScore;

    private boolean submitted;
    private LocalDateTime submittedAt;
    private String submittedByName;
    private List<Person> people = new ArrayList<>();
    private List<TrainingAttendanceSheetDto.Evidence> evidence = new ArrayList<>();
    private LocalDateTime generatedAt;
    private String generatedByName;

    @Data
    public static class Person {
        private String name;
        private String email;
        private String roleName;
        private String departmentName;
        /** From the HRDC registration form, when the session had one; for whoever records the attendance. */
        private String branch;
        private String idNumber;
        private boolean walkIn;
        /** ATTENDED, ABSENT or NOT_RECORDED. */
        private String status;
        private Integer quizScore;
        private String method;
        private String recordedByName;
        private LocalDateTime recordedAt;
    }
}
