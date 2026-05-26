package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleCompetencyItemRepository extends JpaRepository<RoleCompetencyItem, RoleCompetencyItemId> {

    List<RoleCompetencyItem> findAllByProposal_Id(Long proposalId);

    List<RoleCompetencyItem> findAllByProposal_IdIn(Set<Long> proposal_id);

    List<RoleCompetencyItem> findAllByCompetency_IdIn(Set<Long> competency_id);

    List<RoleCompetencyItem> findAllByProposal_IdAndStaff_Id(Long proposal_id, UUID staff_id);

    List<RoleCompetencyItem> findAllByProposal_IdInAndStaff_IdIn(Set<Long> proposal_id, Set<UUID> staff_ids);

    Long proposal(Proposal proposal);
}
