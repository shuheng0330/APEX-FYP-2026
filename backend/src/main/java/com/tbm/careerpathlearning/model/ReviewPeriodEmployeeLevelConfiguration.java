package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "review_period_employee_level_configuration", uniqueConstraints = {
    @UniqueConstraint(name = "uq_period_employee_level", columnNames = {"review_period_id", "employee_level_id"}),
    @UniqueConstraint(name = "uq_period_level_config_id", columnNames = {"review_period_id", "id"})
})
public class ReviewPeriodEmployeeLevelConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_period_id", nullable = false, foreignKey = @ForeignKey(name = "fk_period_level_period"))
    private AnnualKpiReviewPeriod reviewPeriod;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_level_id", nullable = false, foreignKey = @ForeignKey(name = "fk_period_level_employee_level"))
    private EmployeeLevel employeeLevel;
    @Column(name = "company_kpi_weight", precision = 5, scale = 2)
    private BigDecimal companyKpiWeight;
    @Column(name = "department_kpi_weight", precision = 5, scale = 2)
    private BigDecimal departmentKpiWeight;
    @Column(name = "individual_kpi_weight", precision = 5, scale = 2)
    private BigDecimal individualKpiWeight;
}
