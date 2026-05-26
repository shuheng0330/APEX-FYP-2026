package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CompetencyProposal;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface CompetencyProposalRepository extends JpaRepository<CompetencyProposal, ProposalParticipantId> {

    Optional<CompetencyProposal> findByNameIgnoreCaseAndCreatedBy(String name, UUID createdBy);

    @Query("SELECT cp FROM CompetencyProposal cp WHERE cp.proposal.id in (:proposalIds)")
    List<CompetencyProposal> findAllByProposalIdIn(@Param("proposalIds") Set<Long> proposalIds);

    void deleteByProposal_Id(Long proposalId);

    @Modifying
    @Query("DELETE FROM CompetencyProposal cp WHERE cp.proposal.id IN (:proposalIds)")
    void deleteAllByProposalIdIn(@Param("proposalIds") Set<Long> proposalIds);

    List<CompetencyProposal> findAllByProposal_Id(Long proposalId);

    List<CompetencyProposal> findAllByProposal_IdIn(Set<Long> proposalIds);

    List<CompetencyProposal> findAllByStaff_Id(UUID staffId);
}
