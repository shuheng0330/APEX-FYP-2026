package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** The HRDC registration form, filled in from the staff record where it can be (FR-13-03). */
@Data
public class TrainingRegistrationFormDto {
    private String name;
    private String email;
    private String departmentName;
    private String roleName;
    /** TBM's branches by region. */
    private Map<String, List<String>> branches;
}
