package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Staff Sharing page (FR-15-01 to FR-15-03): a year's sharing sessions whose attendance is submitted, who presented
 * them and how many colleagues they reached, and the upcoming ones. The figures and presenters are company-wide; the
 * sessions listed are the ones the viewer may see, as on the calendar (decision A, 29 Oct 2026).
 */
@Data
public class TrainingSharingBoardDto {
    private int year;
    /** Years with sharing sessions, newest first, for the year picker; always includes this year. */
    private List<Integer> years = new ArrayList<>();

    private int sessionsHeld;
    private int presenters;
    /** Every attendance, counting someone who went to three sessions three times. */
    private int totalAttendances;
    /** Different staff who attended at least one, still with the company: never more than the headcount (TF-23). */
    private int staffReached;
    /** Active staff now. */
    private int headcount;
    private Integer reachPercent;
    /** Attendances per session held, to one decimal place; null when none were held. */
    private Double averageAttendance;
    private Integer averageQuizScore;

    private List<Presenter> staffWhoShared = new ArrayList<>();
    private List<Held> sessions = new ArrayList<>();
    /** Sharing sessions still to come or under way that the viewer may see. */
    private List<TrainingSessionDto> upcoming = new ArrayList<>();

    /** A presenter's contribution in the year (FR-15-02). */
    @Data
    public static class Presenter {
        private UUID staffId;
        private String name;
        private String roleName;
        private String departmentName;
        private int sessions;
        private int attendances;
        private int staffReached;
        private Integer averageQuizScore;
        private LocalDate lastShared;
        /** No longer with the company; their sharing still counts. */
        private boolean leftCompany;
    }

    /** A sharing session held, with its attendance submitted. */
    @Data
    public static class Held {
        private Long id;
        private String title;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
        private String presenterName;
        private int attended;
        /** Everyone on its attendance list: registered, and any walk-ins. */
        private int onList;
    }
}
