package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CompetencyProposalDto;
import com.tbm.careerpathlearning.model.ProposalParticipantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CompetencyProposalService {
    List<CompetencyProposalDto> getAll();

    List<CompetencyProposalDto> getAllByIdIn(Set<ProposalParticipantId> ids);

    CompetencyProposalDto getById(ProposalParticipantId id);

    List<CompetencyProposalDto> getAllByProposalId(Long proposalId);

    List<CompetencyProposalDto> getAllByStaffId(UUID staffId);

    List<CompetencyProposalDto> getAllByProposalIdIn(Set<Long> proposalId);

    List<CompetencyProposalDto> createCompetencyProposals(List<CompetencyProposalDto> dtos);

    CompetencyProposalDto updateCompetencyProposal(ProposalParticipantId id, CompetencyProposalDto dto);

    void delete(ProposalParticipantId id);

    void deleteAllByIdIn(Set<ProposalParticipantId> proposalParticipantIds);

    void deleteAllByProposalId(Long proposalId);

    List<CompetencyProposalDto> findAndDeleteAllByProposalIdIn(Set<Long> proposalIds);

    void deleteAllByProposalIdIn(Set<Long> proposalIds);
}
