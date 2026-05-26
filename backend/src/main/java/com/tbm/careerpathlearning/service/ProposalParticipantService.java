package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.ProposalParticipantDto;
import com.tbm.careerpathlearning.model.ProposalParticipantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProposalParticipantService {

    List<ProposalParticipantDto> getAllProposalParticipants();

    List<ProposalParticipantDto> getAllByIdIn(Set<ProposalParticipantId> proposalParticipantIds);

    List<ProposalParticipantDto> getAllByProposalId(Long proposalId);

    List<ProposalParticipantDto> getAllByStaffId(UUID staffId);

    List<ProposalParticipantDto> getAllByProposalIdIn(Set<Long> proposalId);

    List<ProposalParticipantDto> createProposalParticipants(List<ProposalParticipantDto> proposalParticipantDtos);

    List<ProposalParticipantDto> getByStaffRoleIdIn(Set<Long> roleId);

    void deleteById(ProposalParticipantId proposalParticipantIds);

    ProposalParticipantDto findAndDeleteById(ProposalParticipantId proposalParticipantIds);

    List<ProposalParticipantDto> findAndDeleteByProposalId(Long proposalId);

    List<ProposalParticipantDto> findAndDeleteByProposalIdIn(Set<Long> proposalIds);

    List<ProposalParticipantDto> findAndDeleteByIdIn(Set<ProposalParticipantId> ids);

    void deleteAllByIdIn(Set<ProposalParticipantId> proposalParticipantIds);

    void deleteAllByProposalId(Long proposalId);

    void deleteAllByProposalIdIn(Set<Long> proposalIds);
}
