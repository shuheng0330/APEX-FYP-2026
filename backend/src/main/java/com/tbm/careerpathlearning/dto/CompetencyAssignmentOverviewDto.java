package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompetencyAssignmentOverviewDto {

    private Long orgChartId;
    private String orgChartName;
    private boolean isOrgChartDeleted;
    private Long roleId;
    private String roleName;
    private int totalWeightage;
    private List<CompetencyAssignmentDto> competencyAssignments;
    private Map<Long, List<String>> compTags;

    public Long getOrgChartId() {
        return orgChartId;
    }

    public void setOrgChartId(Long orgChartId) {
        this.orgChartId = orgChartId;
    }

    public String getOrgChartName() {
        return orgChartName;
    }

    public void setOrgChartName(String orgChartName) {
        this.orgChartName = orgChartName;
    }

    public boolean isOrgChartDeleted() {
        return isOrgChartDeleted;
    }

    public void setOrgChartDeleted(boolean orgChartDeleted) {
        isOrgChartDeleted = orgChartDeleted;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getTotalWeightage() {
        return totalWeightage;
    }

    public void setTotalWeightage(int totalWeightage) {
        this.totalWeightage = totalWeightage;
    }

    public List<CompetencyAssignmentDto> getCompetencyAssignments() {
        return competencyAssignments;
    }

    public void setCompetencyAssignments(List<CompetencyAssignmentDto> competencyAssignments) {
        this.competencyAssignments = competencyAssignments;
    }

    public Map<Long, List<String>> getCompTags() {
        return compTags;
    }

    public void setCompTags(Map<Long, List<String>> compTags) {
        this.compTags = compTags;
    }
}
