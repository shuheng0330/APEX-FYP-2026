package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;
import com.tbm.careerpathlearning.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    List<Proposal> findAllByStatus(ProposalStatus status);

//    @Query("SELECT p FROM Proposal p WHERE p.status IN (:statuses)")
    List<Proposal> findAllByStatusIn(Set<ProposalStatus> statuses);

    List<Proposal> findAllByType(ProposalType type);

//    @Query("SELECT p FROM Proposal p WHERE p.type IN (:types)")
    List<Proposal> findAllByTypeIn(Set<ProposalType> types);
}
