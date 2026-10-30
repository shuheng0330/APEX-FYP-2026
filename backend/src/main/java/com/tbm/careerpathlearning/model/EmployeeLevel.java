package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "employee_level")
public class EmployeeLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "display_order", nullable = false, unique = true)
    private Integer displayOrder;
    @Column(name = "default_company_kpi_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultCompanyKpiWeight;
    @Column(name = "default_department_kpi_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultDepartmentKpiWeight;
    @Column(name = "default_individual_kpi_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultIndividualKpiWeight;
}
