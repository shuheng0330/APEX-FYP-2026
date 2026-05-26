package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathwayAssignmentOverviewDto {

    private Long orgChartId;
    private String orgChartName;
    private boolean isOrgChartDeleted;
    private Long careerPathwayId;
    private String careerPathwayName;
    private List<StaffDto> staffs;

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

    public List<StaffDto> getStaffs() {
        return staffs;
    }

    public void setStaffs(List<StaffDto> staffs) {
        this.staffs = staffs;
    }
}
