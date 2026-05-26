package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CompetencyCompTagId implements Serializable {

    @Column(name = "competency_id")
    private Long competencyId;

    @Column(name = "comp_tag_id")
    private Long compTagId;

    public CompetencyCompTagId() {
    }

    public CompetencyCompTagId(Long competencyId, Long compTagId) {
        this.competencyId = competencyId;
        this.compTagId = compTagId;
    }

    public Long getCompetencyId() {
        return competencyId;
    }

    public void setCompetencyId(Long competencyId) {
        this.competencyId = competencyId;
    }

    public Long getCompTagId() {
        return compTagId;
    }

    public void setCompTagId(Long compTagId) {
        this.compTagId = compTagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompetencyCompTagId)) return false;
        CompetencyCompTagId that = (CompetencyCompTagId) o;
        return Objects.equals(competencyId, that.competencyId) && Objects.equals(compTagId, that.compTagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(competencyId, compTagId);
    }
}

