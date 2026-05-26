package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProposalDto {

    private Long id;
    private ProposalType type;
    private ProposalStatus status;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public ProposalDto(ProposalType type, ProposalStatus status, UUID createdBy, OffsetDateTime createdAt, UUID updatedBy, OffsetDateTime updatedAt) {
        this.type = type;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProposalType getType() {
        return type;
    }

    public void setType(ProposalType type) {
        this.type = type;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ProposalStatus status) {
        this.status = status;
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

    @Override
    public String toString() {
        return "[ id = " + this.id + ", "
                + "type = " + this.type + ", "
                + "status = " + this.status.getProposalStatus() + ", "
                + "createdBy = " + this.createdBy + ", "
                + "createdAt = " + this.createdAt + ", "
                + "updatedBy = " + this.updatedBy + ", "
                + "updatedAt = " + this.updatedAt
                + "]";
    }
}
