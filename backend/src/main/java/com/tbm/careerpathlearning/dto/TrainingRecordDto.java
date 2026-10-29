package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A staff member's training record (FR-18): every course and every session with submitted attendance, the totals, and
 * the SOP certificates. Seen by the person, their manager, HR and the Super Admin (decision A, 29 Oct 2026).
 */
@Data
public class TrainingRecordDto {
    private UUID staffId;
    private String name;
    private String email;
    private String roleName;
    private String departmentName;
    /** No longer with the company: the record is kept. */
    private boolean leftCompany;

    /** FR-18-03: completed courses and attended sessions. */
    private int completedItems;
    private double courseHours;
    private double sessionHours;
    private double totalHours;
    private LocalDate lastCompleted;
    /** Mandatory courses still to finish. */
    private int mandatoryOutstanding;

    private List<Item> items = new ArrayList<>();
    private List<Certificate> certificates = new ArrayList<>();

    /** One course or session (FR-18-01). */
    @Data
    public static class Item {
        /** COURSE or SESSION. */
        private String type;
        private Long id;
        private String title;
        /** Courses only: v1, v2... */
        private Integer versionNo;
        /**
         * NOT_STARTED, IN_PROGRESS, OVERDUE, COMPLETED or WITHDRAWN for a course (FR-18-02); ATTENDED or ABSENT for a
         * session.
         */
        private String status;
        private boolean mandatory;
        /** E-learning for a course; Physical, Hybrid or Online for a session. */
        private String delivery;
        /** Sessions only: Training or Staff Sharing, and its training type. */
        private String kind;
        private String trainingType;
        /** Due and completed are kept apart (TF-17); a session has the day it was held instead of a due date. */
        private LocalDate dueDate;
        private LocalDate completedOn;
        private LocalDate heldOn;
        /** The course's final score, or the session's quiz score. */
        private Integer score;
        /** Hours counted towards the totals: completed courses and attended sessions only. */
        private Double hours;
        /** Courses only: each module's quiz result. */
        private List<ModuleScore> modules = new ArrayList<>();
    }

    @Data
    public static class ModuleScore {
        private String title;
        /** The passing score, or the best so far when not passed yet; null before any attempt. */
        private Integer score;
        private boolean passed;
        private int attempts;
    }

    /** FR-18-05 */
    @Data
    public static class Certificate {
        private String certificateNo;
        private String course;
        private int versionNo;
        private LocalDate issuedOn;
        private Integer finalScore;
    }
}
