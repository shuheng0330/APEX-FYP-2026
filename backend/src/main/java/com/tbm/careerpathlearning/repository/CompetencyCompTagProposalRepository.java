package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CompetencyCompTagProposalRepository extends JpaRepository<CompetencyCompTagProposal, CompetencyCompTagProposalId> {

    List<CompetencyCompTagProposal> findByProposalIdAndStaffId(Long proposalId, UUID staffId);

    @Query("SELECT ctp FROM CompetencyCompTagProposal ctp " +
            "WHERE ctp.proposal.id IN (:proposalIds) AND ctp.staff.id IN (:staffIds)")
    List<CompetencyCompTagProposal> findByProposalIdInAndStaffIdIn(@Param("proposalIds") Set<Long> proposalIds, @Param("staffIds") Set<UUID> staffIds);

    List<CompetencyCompTagProposal> findAllByProposal_Id(Long proposalId);

    @Modifying
    @Query("DELETE FROM CompetencyCompTagProposal ctp WHERE ctp.proposal.id = :proposalId AND ctp.staff.id = :staffId AND ctp.compTag.id IN (:compTagIds)")
    void deleteByProposalIdAndStaffIdAndCompTagIdIn(@Param("proposalId") Long proposalId, @Param("staffId") UUID staffId, @Param("compTagIds") Set<Long> compTagIds);

    @Modifying
    @Query("DELETE FROM CompetencyCompTagProposal ctp WHERE ctp.proposal.id IN (:proposalIds) AND ctp.staff.id IN (:staffIds) AND ctp.compTag.id IN :compTagIds")
    void deleteByProposalIdInAndStaffIdInAndCompTagIdIn(@Param("proposalIds") Set<Long> proposalIds, @Param("staffIds") Set<UUID> staffIds, @Param("compTagIds") Set<Long> compTagIds);

    List<CompetencyCompTagProposal> findAllByProposal_IdIn(Set<Long> proposalIds);

    @Query("SELECT ctp FROM CompetencyCompTagProposal ctp WHERE ctp.compTag.id IN (:compTagIds)")
    List<CompetencyCompTagProposal> findAllByCompTagIdIn(@Param("compTagIds") Set<Long> compTagIds);

    void deleteAllByProposal_Id(Long proposalId);

    void deleteAllByProposal_IdIn(Set<Long> proposalIds);
}
