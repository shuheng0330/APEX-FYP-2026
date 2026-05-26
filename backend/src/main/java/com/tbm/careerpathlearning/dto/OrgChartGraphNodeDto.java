package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrgChartGraphNodeDto {

    private String type;
    private OrgChartDepartmentNodeDto departmentNode;
    private OrgChartPersonNodeDto personNode;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public OrgChartDepartmentNodeDto getDepartmentNode() {
        return departmentNode;
    }

    public void setDepartmentNode(OrgChartDepartmentNodeDto departmentNode) {
        this.departmentNode = departmentNode;
    }

    public OrgChartPersonNodeDto getPersonNode() {
        return personNode;
    }

    public void setPersonNode(OrgChartPersonNodeDto personNode) {
        this.personNode = personNode;
    }
}
