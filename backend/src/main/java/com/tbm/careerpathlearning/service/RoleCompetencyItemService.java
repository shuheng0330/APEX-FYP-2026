package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.RoleCompetencyItemDto;
import com.tbm.careerpathlearning.model.RoleCompetencyItemId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleCompetencyItemService {

    List<RoleCompetencyItemDto> findAllByIdIn(Set<RoleCompetencyItemId> ids);

    List<RoleCompetencyItemDto> findAllByProposalStaffId(Long proposalId, UUID staffId);

    List<RoleCompetencyItemDto> findAllByProposalIdIn(Set<Long> proposalIds);

    List<RoleCompetencyItemDto> createAll(List<RoleCompetencyItemDto> dtos);

    List<RoleCompetencyItemDto> updateAll(Set<RoleCompetencyItemId> ids, List<RoleCompetencyItemDto> dtos);

    List<RoleCompetencyItemDto> findAndDeleteAllByProposalStaffId(Long proposalId, UUID staffId);

    List<RoleCompetencyItemDto> findAndDeleteAllByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds);

    List<RoleCompetencyItemDto> findAndDeleteAllByProposalId(Long proposalId);

    void deleteAllByIdIn(Set<RoleCompetencyItemId> ids);
}
