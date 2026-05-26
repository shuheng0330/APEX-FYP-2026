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
public class CareerPathwayTrackId implements Serializable {

    @Column(name = "career_pathway_id")
    private Long careerPathwayId;

    @Column(name = "track_id")
    private Long trackId;

    public Long getCareerPathwayId() {
        return careerPathwayId;
    }

    public void setCareerPathwayId(Long competencyId) {
        this.careerPathwayId = competencyId;
    }

    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long compTagId) {
        this.trackId = compTagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareerPathwayTrackId)) return false;
        CareerPathwayTrackId that = (CareerPathwayTrackId) o;
        return Objects.equals(careerPathwayId, that.careerPathwayId) && Objects.equals(trackId, that.trackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(careerPathwayId, trackId);
    }

    @Override
    public String toString() {
        return "[ careerPathwayId = " + careerPathwayId + ", "
                + "trackId = " + trackId + " ]";
    }
}

