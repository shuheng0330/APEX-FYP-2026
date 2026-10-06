package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "review_period_role_configuration", uniqueConstraints =
        @UniqueConstraint(name = "uq_review_period_role", columnNames = {"review_period_id", "role_id"}),
        indexes = @Index(name = "idx_review_period_role_role", columnList = "role_id"))
public class ReviewPeriodRoleConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_period_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_period_role_period"))
    private AnnualKpiReviewPeriod reviewPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_period_role_role"))
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_frequency", nullable = false, length = 20)
    private ReviewFrequency reviewFrequency;

    public static ReviewFrequency resolveFrequency(Role role, ReviewFrequency override) {
        if (override != null) return override;
        if (role != null && role.getDefaultReviewFrequency() != null) return role.getDefaultReviewFrequency();
        return ReviewFrequency.ANNUALLY;
    }
}
