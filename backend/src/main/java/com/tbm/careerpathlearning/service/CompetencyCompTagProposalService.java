package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CompetencyCompTagProposalDto;
import com.tbm.careerpathlearning.model.CompetencyCompTagProposalId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CompetencyCompTagProposalService {
    List<CompetencyCompTagProposalDto> getAll();

    List<CompetencyCompTagProposalDto> findAllByIdIn(Set<CompetencyCompTagProposalId> competencyCompTagIds);

    List<CompetencyCompTagProposalDto> findAllByProposalId(Long proposalId);

    List<CompetencyCompTagProposalDto> findAllByProposalIdIn(Set<Long> proposalIds);

    List<CompetencyCompTagProposalDto> getByCompTagIdIn(Set<Long> compTagIds);

    List<CompetencyCompTagProposalDto> createCompetencyCompTagProposals(List<CompetencyCompTagProposalDto> dtos);

    List<CompetencyCompTagProposalDto> getByProposalStaffId(Long proposalId, UUID staffId);

    List<CompetencyCompTagProposalDto> getByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds);

    void deleteByProposalStaffIdAndCompTagIdIn(Long proposalId, UUID staffId, Set<Long> compTagIds);

    void deleteByProposalStaffIdInAndCompTagIdIn(Set<Long> proposalId, Set<UUID> staffId, Set<Long> compTagIds);

    void deleteAllByIdIn(Set<CompetencyCompTagProposalId> ids);

    void deleteAllByProposalId(Long proposalId);

    void deleteAllByProposalIdIn(Set<Long> proposalIds);

    List<CompetencyCompTagProposalDto> findAndDeleteAllByProposalIdIn(Set<Long> proposalIds);
}
