package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrgWideDepartmentTrendDto {
    private Integer year;
    private List<OrgWideDepartmentTrendDepartmentDto> departments;
}
