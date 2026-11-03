package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EmployeeLevelDto {
    private Long id;
    private String code;
    private String name;
    private Integer displayOrder;
    private BigDecimal defaultCompanyKpiWeight;
    private BigDecimal defaultDepartmentKpiWeight;
    private BigDecimal defaultIndividualKpiWeight;
}
