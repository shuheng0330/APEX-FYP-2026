package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.ProposalDto;
import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProposalService {

    List<ProposalDto> getAll();

    ProposalDto getProposalById(Long id);

    List<ProposalDto> getAllProposalsByIdIn(Set<Long> ids);

    List<ProposalDto> getAllByType(ProposalType type);

    ProposalDto createProposal(ProposalDto proposalDto);

    ProposalDto updateProposalStatusById(Long proposalId, ProposalStatus proposalStatus, UUID userId);

    List<ProposalDto> updateProposalStatusByIdIn(Set<Long> proposalId, ProposalStatus proposalStatus, UUID userId);
}
