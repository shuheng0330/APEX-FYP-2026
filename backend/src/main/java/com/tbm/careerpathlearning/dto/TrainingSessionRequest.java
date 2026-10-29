package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A session as the trainer schedules or changes it (FR-12-01 to FR-12-04, FR-12-12, FR-12-13, FR-17-01). */
@Data
public class TrainingSessionRequest {
    private String title;
    private String description;
    private TrainingSessionKind kind;
    private TrainingDelivery delivery;
    /** Physical and Hybrid training sessions only; ignored otherwise. */
    private TrainingType trainingType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private Integer capacity;
    private boolean mandatory;
    /** A staff member with Trainer access, or HR, who runs or presents the session... */
    private UUID trainerId;
    /** ...or an external trainer's name, for a training session. */
    private String trainerName;
    /** The target audience; nobody chosen means everyone. */
    private List<Long> departmentIds = new ArrayList<>();
    private List<Long> roleIds = new ArrayList<>();
    private List<UUID> staffIds = new ArrayList<>();
    private boolean selfRegistration = true;
    private boolean registrationForm;
    private LocalDate registrationCutoff;
    private boolean waitlistEnabled = true;
    /** Also save this set-up as a reusable template, under this name (FR-12-08). */
    private String templateName;
    /** The template the form was filled from, counted as used once the session is scheduled (FR-12-09). */
    private Long templateId;
}
