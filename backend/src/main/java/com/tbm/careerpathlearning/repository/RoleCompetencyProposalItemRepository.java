package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface RoleCompetencyProposalItemRepository extends JpaRepository<RoleCompetencyProposalItem, RoleCompetencyProposalItemId> {

    List<RoleCompetencyProposalItem> findAllByStaff_Id(UUID staffId);

    List<RoleCompetencyProposalItem> findAllByProposal_Id(Long proposalId);

    List<RoleCompetencyProposalItem> findAllByProposal_IdIn(Set<Long> proposal_id);

    List<RoleCompetencyProposalItem> findAllByProposal_IdAndStaff_Id(Long proposal_id, UUID staff_id);

    List<RoleCompetencyProposalItem> findAllByProposal_IdInAndStaff_IdIn(Set<Long> proposal_ids, Set<UUID> staff_ids);

    List<RoleCompetencyProposalItem> findAllByProposal_IdAndRole_IdAndStaff_Id(Long proposal_id, Long role_id, UUID staff_id);
}
