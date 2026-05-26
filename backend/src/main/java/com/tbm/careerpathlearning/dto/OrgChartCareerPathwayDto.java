package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrgChartCareerPathwayDto {

    private Long orgChartId;
    private String orgChartName;
    private Long careerPathwayId;
    private String careerPathwayName;
    private Set<Long> childrenRoleIds;

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

    public Long getCareerPathwayId() {
        return careerPathwayId;
    }

    public void setCareerPathwayId(Long careerPathwayId) {
        this.careerPathwayId = careerPathwayId;
    }

    public String getCareerPathwayName() {
        return careerPathwayName;
    }

    public void setCareerPathwayName(String careerPathwayName) {
        this.careerPathwayName = careerPathwayName;
    }

    public Set<Long> getChildrenRoleIds() {
        return childrenRoleIds;
    }

    public void setChildrenRoleIds(Set<Long> childrenRoleIds) {
        this.childrenRoleIds = childrenRoleIds;
    }
}
