package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.Status;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Table(name="TRAINING_PROGRAM")
public class TrainingProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trainingId;

    private String title;

    private String description;

    private String venue;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "is_public")
    private Boolean isPublic;

    private UUID createdBy;   // waiting for staff entity to create

    private LocalDateTime createdAt;

    private UUID updatedBy; // waiting for staff entity to create

    private LocalDateTime updatedAt;

    @Column(name = "is_mandatory")
    private Boolean isMandatory;

    @Column(name = "important_notes", columnDefinition = "TEXT[]")
    private List<String> importantNotes;

    @OneToMany(mappedBy = "training", fetch = FetchType.LAZY)
    private List<TrainingTargetRole> trainingTargetRoles;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "training_program_org_chart",
            joinColumns = @JoinColumn(name = "training_id"),
            inverseJoinColumns = @JoinColumn(name = "org_chart_id")
    )
    private List<OrgChart> departments;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "checkin_radius_meters")
    private Double checkinRadius; // default 100 meters

    @ManyToMany
    @JoinTable( name = "training_program_competency", joinColumns = @JoinColumn(name = "training_id"), inverseJoinColumns = @JoinColumn( name = "competency_id"))
    @org.hibernate.annotations.SQLRestriction("is_deleted = false")
    private List<Competency> competencies;
}
