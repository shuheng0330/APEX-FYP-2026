package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleAuthorityOverviewDto {

    private Long orgChartId;
    private String orgChartName;
    private boolean isOrgChartDeleted;
    private List<RoleAuthorityMapDto> roles;

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

    public List<RoleAuthorityMapDto> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleAuthorityMapDto> roles) {
        this.roles = roles;
    }
}
