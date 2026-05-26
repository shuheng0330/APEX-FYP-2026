package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.RoleJobScopeProposalDto;
import com.tbm.careerpathlearning.model.RoleJobScopeProposalId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleJobScopeProposalService {

    List<RoleJobScopeProposalDto> findAllByIdIn(Set<RoleJobScopeProposalId> ids);

    List<RoleJobScopeProposalDto> findAllByProposalIdIn(Set<Long> proposalIds);

    List<RoleJobScopeProposalDto> findAllByJobScopeIdIn(Set<Long> jobScopeIds);

    List<RoleJobScopeProposalDto> createAll(List<RoleJobScopeProposalDto> dtos);

    List<RoleJobScopeProposalDto> getByRoleCompetencyProposalId(Long proposalId, Long roleId, UUID staffId);

    List<RoleJobScopeProposalDto> findAndDeleteAllByProposalId(Long proposalId);

    List<RoleJobScopeProposalDto> findAndDeleteAllByProposalStaffId(Long proposalId, UUID staffId);

    List<RoleJobScopeProposalDto> findAndDeleteAllByProposalStaffIdIn(Set<Long> proposalIds, Set<UUID> staffIds);

    void deleteAllByIdIn(Set<RoleJobScopeProposalId> ids);
}
