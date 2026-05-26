package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.RoleCompetencyProposal;
import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface RoleCompetencyProposalRepository extends JpaRepository<RoleCompetencyProposal, RoleCompetencyProposalId> {

    List<RoleCompetencyProposal> findAllByProposal_IdIn(Set<Long> proposal_id);

    List<RoleCompetencyProposal> findAllByProposal_Id(Long proposalId);

}
