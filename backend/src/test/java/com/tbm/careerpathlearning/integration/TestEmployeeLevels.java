package com.tbm.careerpathlearning.integration;

import com.tbm.careerpathlearning.model.EmployeeLevel;
import com.tbm.careerpathlearning.repository.EmployeeLevelRepository;

import java.math.BigDecimal;

/**
 * Employee Level fixture for the H2 integration tests. Roles now require an Employee Level, and the H2 test
 * database is built by Hibernate without the migrations that seed the real levels, so tests create this one.
 * It is a test classification only, not a business mapping.
 */
final class TestEmployeeLevels {

    static final String CODE = "TEST_LEVEL";

    private TestEmployeeLevels() {
    }

    static EmployeeLevel findOrCreate(EmployeeLevelRepository employeeLevelRepository) {
        return employeeLevelRepository.findByCodeIgnoreCase(CODE).orElseGet(() -> {
            EmployeeLevel level = new EmployeeLevel();
            level.setCode(CODE);
            level.setName("Test Level");
            level.setDisplayOrder(9001);
            level.setDefaultCompanyKpiWeight(new BigDecimal("20.00"));
            level.setDefaultDepartmentKpiWeight(new BigDecimal("30.00"));
            level.setDefaultIndividualKpiWeight(new BigDecimal("50.00"));
            return employeeLevelRepository.save(level);
        });
    }
}
