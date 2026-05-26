package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "competency_comp_tag")
public class CompetencyCompTag {

    @EmbeddedId
    private CompetencyCompTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("competencyId")
    @JoinColumn(name = "competency_id")
    private Competency competency;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("compTagId")
    @JoinColumn(name = "comp_tag_id")
    private CompTag compTag;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public CompetencyCompTag() {
    }

    public CompetencyCompTag(Competency competency, CompTag compTag) {
        this.competency = competency;
        this.compTag = compTag;
        this.id = new CompetencyCompTagId(competency.getId(), compTag.getId());
    }

    public CompetencyCompTagId getId() {
        return id;
    }

    public void setId(CompetencyCompTagId id) {
        this.id = id;
    }

    public Competency getCompetency() {
        return competency;
    }

    public void setCompetency(Competency competency) {
        this.competency = competency;
    }

    public CompTag getCompTag() {
        return compTag;
    }

    public void setCompTag(CompTag compTag) {
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
