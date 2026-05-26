package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCareerPathwayRequestDto {

    private Long careerPathwayId;
    private Long orgChartId;
    private String careerPathwayName;
    private String description;
    private List<String> track;
    private Long rootRoleId;
    private Map<Long, Set<Long>> parentChildRoleId;

    public Long getCareerPathwayId() {
        return careerPathwayId;
    }

    public void setCareerPathwayId(Long careerPathwayId) {
        this.careerPathwayId = careerPathwayId;
    }

    public Long getOrgChartId() {
        return orgChartId;
    }

    public void setOrgChartId(Long orgChartId) {
        this.orgChartId = orgChartId;
    }

    public String getCareerPathwayName() {
        return careerPathwayName;
    }

    public void setCareerPathwayName(String careerPathwayName) {
        this.careerPathwayName = careerPathwayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTrack() {
        return track;
    }

    public void setTrack(List<String> track) {
        this.track = track;
    }

    public Long getRootRoleId() {
        return rootRoleId;
    }

    public void setRootRoleId(Long rootRoleId) {
        this.rootRoleId = rootRoleId;
    }

    public Map<Long, Set<Long>> getParentChildRoleId() {
        return parentChildRoleId;
    }

    public void setParentChildRoleId(Map<Long, Set<Long>> parentChildRoleId) {
        this.parentChildRoleId = parentChildRoleId;
    }
}
