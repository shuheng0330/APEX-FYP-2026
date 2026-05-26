package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.RoleCompetencyProposalItemDto;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalItemId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleCompetencyProposalItemService {

    List<RoleCompetencyProposalItemDto> findAllByIdIn(Set<RoleCompetencyProposalItemId> ids);

    List<RoleCompetencyProposalItemDto> findAllByProposalStaffId(Long proposalId, UUID staffId);

    List<RoleCompetencyProposalItemDto> findAllByProposalIdIn(Set<Long> proposalIds);

    List<RoleCompetencyProposalItemDto> findAllByRoleCompetencyProposalId(Long proposalId, Long roleId, UUID staffId);

    List<RoleCompetencyProposalItemDto> createAll(List<RoleCompetencyProposalItemDto> dtos);

    List<RoleCompetencyProposalItemDto> updateAll(Set<RoleCompetencyProposalItemId> ids, List<RoleCompetencyProposalItemDto> dtos);

    List<RoleCompetencyProposalItemDto> findAndDeleteAllByProposalId(Long proposalId);

    List<RoleCompetencyProposalItemDto> findAndDeleteAllByProposalStaffId(Long proposalId, UUID staffId);

    List<RoleCompetencyProposalItemDto> findAndDeleteAllByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds);

    void deleteAllByIdIn(Set<RoleCompetencyProposalItemId> ids);
}
