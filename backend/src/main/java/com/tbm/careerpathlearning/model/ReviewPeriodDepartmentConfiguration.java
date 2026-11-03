package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name = "review_period_department_configuration")
public class ReviewPeriodDepartmentConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_period_id", nullable = false) private AnnualKpiReviewPeriod reviewPeriod;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false) private OrgChart department;
    @Column(nullable = false) private String departmentName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReviewFrequency reviewFrequency;
}
