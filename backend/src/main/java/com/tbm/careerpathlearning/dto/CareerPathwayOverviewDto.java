package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathwayOverviewDto {

    private Long orgChartId;
    private String orgChartName;
    private boolean isOrgChartDeleted;
    private Long careerPathwayId;
    private String careerPathwayName;
    private String careerPathwayDescription;
    private List<TrackDto> trackDtoList;
    private CareerPathwayNodeDto graph;
    private String currentRoleName;

    public CareerPathwayOverviewDto(Long orgChartId, String orgChartName, boolean isOrgChartDeleted, Long careerPathwayId, String careerPathwayName, String careerPathwayDescription, List<TrackDto> trackDtoList, CareerPathwayNodeDto graph) {
        this.orgChartId = orgChartId;
        this.orgChartName = orgChartName;
        this.isOrgChartDeleted = isOrgChartDeleted;
        this.careerPathwayId = careerPathwayId;
        this.careerPathwayName = careerPathwayName;
        this.careerPathwayDescription = careerPathwayDescription;
        this.trackDtoList = trackDtoList;
        this.graph = graph;
    }

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

    public String getCareerPathwayDescription() {
        return careerPathwayDescription;
    }

    public void setCareerPathwayDescription(String careerPathwayDescription) {
        this.careerPathwayDescription = careerPathwayDescription;
    }

    public List<TrackDto> getTrackDtoList() {
        return trackDtoList;
    }

    public void setTrackDtoList(List<TrackDto> trackDtoList) {
        this.trackDtoList = trackDtoList;
    }

    public CareerPathwayNodeDto getGraph() {
        return graph;
    }

    public void setGraph(CareerPathwayNodeDto graph) {
        this.graph = graph;
    }

    public String getCurrentRoleName() {
        return currentRoleName;
    }

    public void setCurrentRoleName(String currentRoleName) {
        this.currentRoleName = currentRoleName;
    }
}
