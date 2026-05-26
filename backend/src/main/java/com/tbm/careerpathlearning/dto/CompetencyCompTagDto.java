package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompetencyCompTagDto {

    private CompetencyCompTagId id;
    private CompetencyDto competency;
    private CompTagDto compTag;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public CompetencyCompTagId getId() {
        return id;
    }

    public void setId(CompetencyCompTagId id) {
        this.id = id;
    }

    public CompetencyDto getCompetency() {
        return competency;
    }

    public void setCompetency(CompetencyDto competency) {
        this.competency = competency;
    }

    public CompTagDto getCompTag() {
        return compTag;
    }

    public void setCompTag(CompTagDto compTag) {
        this.compTag = compTag;
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
