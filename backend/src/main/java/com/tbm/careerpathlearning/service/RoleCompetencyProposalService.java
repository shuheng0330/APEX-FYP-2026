package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.RoleCompetencyProposalDto;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;

import java.util.List;
import java.util.Set;

public interface RoleCompetencyProposalService {

    List<RoleCompetencyProposalDto> getAllByIdIn(Set<RoleCompetencyProposalId> ids);

    RoleCompetencyProposalDto getById(RoleCompetencyProposalId id);

    List<RoleCompetencyProposalDto> getAllByProposalId(Long proposalId);

    List<RoleCompetencyProposalDto> getAllByProposalIdIn(Set<Long> proposalId);

    List<RoleCompetencyProposalDto> createAll(List<RoleCompetencyProposalDto> dtos);

    RoleCompetencyProposalDto update(RoleCompetencyProposalId id, RoleCompetencyProposalDto dto);

    List<RoleCompetencyProposalDto> findAndDeleteByIdIn(Set<RoleCompetencyProposalId> ids);

    List<RoleCompetencyProposalDto> findAndDeleteByProposalId(Long proposalId);

    List<RoleCompetencyProposalDto> findAndDeleteByProposalIdIn(Set<Long> proposalIds);

    RoleCompetencyProposalDto findAndDeleteById(RoleCompetencyProposalId id);
}
