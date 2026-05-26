package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.RoleJobScopeProposal;
import com.tbm.careerpathlearning.model.RoleJobScopeProposalId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleJobScopeProposalRepository extends JpaRepository<RoleJobScopeProposal, RoleJobScopeProposalId> {

    List<RoleJobScopeProposal> findAllByProposal_Id(Long proposal_id);

    List<RoleJobScopeProposal> findAllByProposal_IdIn(Set<Long> proposal_id);

    List<RoleJobScopeProposal> findAllByProposal_IdAndRole_IdAndStaff_Id(Long proposal_id, Long role_id, UUID staff_id);

    List<RoleJobScopeProposal> findAllByJobScope_IdIn(Set<Long> job_scope_id);

    List<RoleJobScopeProposal> findAllByProposal_IdAndStaff_Id(Long proposal_id, UUID staff_id);

    List<RoleJobScopeProposal> findAllByProposal_IdInAndStaff_IdIn(Set<Long> proposal_ids, Set<UUID> staff_ids);

}
