package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathwayRoleId implements Serializable {

    @Column(name = "pathway_id")
    private Long careerPathwayId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "child_id")
    private Long childId;

    public Long getCareerPathwayId() {
        return careerPathwayId;
    }

    public void setCareerPathwayId(Long careerPathwayId) {
        this.careerPathwayId = careerPathwayId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getChildId() {
        return childId;
    }

    public void setChildId(Long child_id) {
        this.childId = child_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareerPathwayRoleId)) return false;
        CareerPathwayRoleId that = (CareerPathwayRoleId) o;
        return Objects.equals(careerPathwayId, that.careerPathwayId)
                && Objects.equals(parentId, that.parentId)
                && Objects.equals(childId, that.childId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(careerPathwayId, parentId, childId);
    }

    @Override
    public String toString() {
        return "[ pathwayId = " + this.careerPathwayId + ", "
                + "parentId = " + this.parentId + ", "
                + "childId = " + this.childId + "]";
    }
}

