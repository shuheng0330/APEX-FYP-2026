package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The departments, job roles and staff a course can be assigned to (FR-09-02). */
@Data
public class SopAudienceDto {
    private List<Group> departments = new ArrayList<>();
    private List<Group> roles = new ArrayList<>();
    private List<Person> staff = new ArrayList<>();

    @Data
    public static class Group {
        private Long id;
        private String name;
        /** For a job role, the department it belongs to. */
        private String departmentName;
        private int staffCount;
    }

    @Data
    public static class Person {
        private UUID id;
        private String name;
        private String email;
        private Long roleId;
        private String roleName;
        private Long departmentId;
        private String departmentName;
    }
}
