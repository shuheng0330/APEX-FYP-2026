package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "review_period_participant", uniqueConstraints =
        @UniqueConstraint(name = "uq_review_period_participant", columnNames = {"review_period_id", "staff_id"}),
        indexes = {
                @Index(name = "idx_review_participant_staff", columnList = "staff_id,review_period_id"),
                @Index(name = "idx_review_participant_superior", columnList = "review_period_id,superior_id"),
                @Index(name = "idx_review_participant_department", columnList = "review_period_id,department_id"),
                @Index(name = "idx_review_participant_role", columnList = "role_id")
        })
public class ReviewPeriodParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_period_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_participant_period"))
    private AnnualKpiReviewPeriod reviewPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_participant_staff"))
    private Staff staff;

    @Column(name = "staff_name", length = 255, updatable = false)
    private String staffName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_participant_role"))
    private Role role;

    @Column(name = "role_name", length = 255, updatable = false)
    private String roleName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_participant_department"))
    private OrgChart department;

    @Column(name = "department_name", length = 255, updatable = false)
    private String departmentName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superior_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_participant_superior"))
    private Staff superior;

    @Column(name = "superior_name", length = 255, updatable = false)
    private String superiorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_frequency", nullable = false, length = 20, updatable = false)
    private ReviewFrequency reviewFrequency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
        @JoinColumn(name = "review_period_id", referencedColumnName = "review_period_id", insertable = false, updatable = false),
        @JoinColumn(name = "employee_level_configuration_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_participant_level_config"))
    private ReviewPeriodEmployeeLevelConfiguration employeeLevelConfiguration;

    @Column(name = "employee_level_configuration_id", nullable = false, updatable = false)
    private Long employeeLevelConfigurationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initialiseTimestamp() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
