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
import java.util.UUID;

/** A session on the Training Calendar with its seats (FR-12-11, FR-13-04), and what the viewer may do with it. */
@Data
public class TrainingSessionDto {
    private Long id;
    private String title;
    private String description;
    private TrainingSessionKind kind;
    private TrainingDelivery delivery;
    private TrainingType trainingType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private int durationMinutes;
    private String venue;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private int capacity;
    /** Seats taken: registered, or held for someone offered a freed seat. */
    private int booked;
    private int seatsLeft;
    private int waitlisted;
    private boolean mandatory;
    private UUID trainerId;
    /** The staff trainer's or presenter's name, or the external trainer's. */
    private String trainerName;
    private boolean externalTrainer;
    /** The chosen audience, for whoever manages the session; empty for everyone else. */
    private List<Long> departmentIds = new ArrayList<>();
    private List<Long> roleIds = new ArrayList<>();
    private List<UUID> staffIds = new ArrayList<>();
    /** "Everyone", or the chosen departments, job roles and people. */
    private String audience;
    /** Active staff in the audience, not counting whoever runs the session. */
    private int audienceSize;
    /** Set when a mandatory session's audience is larger than its capacity (FR-12-15). */
    private String capacityWarning;
    private boolean selfRegistration;
    private boolean registrationForm;
    private LocalDate registrationCutoff;
    private boolean waitlistEnabled;
    private TrainingSessionStatus status;
    private TrainingSessionTiming timing;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private LocalDateTime archivedAt;
    private String createdByName;
    private LocalDateTime createdAt;
    /** The viewer scheduled the session or is HR, so may change, cancel, archive or delete it. */
    private boolean canManage;
    /** Nobody has registered, been invited or had attendance recorded, so the session can still be deleted (FR-12-06). */
    private boolean canDelete;
    private boolean attendanceRecorded;
}
