package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CompetencyCompTagProposalId implements Serializable {

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "comp_tag_id")
    private Long compTagId;

    public CompetencyCompTagProposalId() {
    }

    public CompetencyCompTagProposalId(Long proposalId, UUID staffId, Long compTagId) {
        this.proposalId = proposalId;
        this.staffId = staffId;
        this.compTagId = compTagId;
    }

    public Long getProposalId() {
        return proposalId;
    }

    public void setProposalId(Long proposalId) {
        this.proposalId = proposalId;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
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
        if (!(o instanceof CompetencyCompTagProposalId)) return false;
        CompetencyCompTagProposalId that = (CompetencyCompTagProposalId) o;
        return Objects.equals(proposalId, that.proposalId) && Objects.equals(staffId, that.staffId) &&
                Objects.equals(compTagId, that.compTagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, staffId, compTagId);
    }
}

