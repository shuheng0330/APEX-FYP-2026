package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ReviewPeriodEmployeeLevelConfigurationDto;
import com.tbm.careerpathlearning.model.EmployeeLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class EmployeeLevelFixtures {
    private EmployeeLevelFixtures() {}

    static List<EmployeeLevel> levels() {
        String[] codes = {"TOP_MANAGEMENT", "MIDDLE_MANAGEMENT", "JUNIOR_MANAGEMENT", "EXECUTIVE", "ADMIN", "GENERAL"};
        int[][] splits = {{50,30,20},{35,35,30},{25,35,40},{15,25,60},{10,10,80},{5,5,90}};
        List<EmployeeLevel> levels = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            var level = new EmployeeLevel();
            level.setId((long) i + 1);
            level.setCode(codes[i]); level.setName(codes[i]); level.setDisplayOrder(i + 1);
            level.setDefaultCompanyKpiWeight(BigDecimal.valueOf(splits[i][0]));
            level.setDefaultDepartmentKpiWeight(BigDecimal.valueOf(splits[i][1]));
            level.setDefaultIndividualKpiWeight(BigDecimal.valueOf(splits[i][2]));
            levels.add(level);
        }
        return levels;
    }

    static List<ReviewPeriodEmployeeLevelConfigurationDto> weights() {
        return new ArrayList<>(levels().stream().map(l -> {
            var dto = new ReviewPeriodEmployeeLevelConfigurationDto();
            dto.setEmployeeLevelId(l.getId());
            dto.setCompanyKpiWeight(l.getDefaultCompanyKpiWeight());
            dto.setDepartmentKpiWeight(l.getDefaultDepartmentKpiWeight());
            dto.setIndividualKpiWeight(l.getDefaultIndividualKpiWeight());
            return dto;
        }).toList());
    }
}
