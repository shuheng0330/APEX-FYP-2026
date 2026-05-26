package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignCareerPathwayRequestDto {

    private Long careerPathwayId;
    private Set<UUID> staffIds;

    public Long getCareerPathwayId() {
        return careerPathwayId;
    }

    public void setCareerPathwayId(Long careerPathwayId) {
        this.careerPathwayId = careerPathwayId;
    }

    public Set<UUID> getStaffIds() {
        return staffIds;
    }

    public void setStaffIds(Set<UUID> staffIds) {
        this.staffIds = staffIds;
    }
}
