package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.CareerPathwayTrackId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CareerPathwayTrackDto {

    private CareerPathwayTrackId id;
    private CareerPathwayDto careerPathway;
    private TrackDto track;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public CareerPathwayTrackId getId() {
        return id;
    }

    public void setId(CareerPathwayTrackId id) {
        this.id = id;
    }

    public CareerPathwayDto getCareerPathway() {
        return careerPathway;
    }

    public void setCareerPathway(CareerPathwayDto careerPathway) {
        this.careerPathway = careerPathway;
    }

    public TrackDto getTrack() {
        return track;
    }

    public void setTrack(TrackDto track) {
        this.track = track;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
