package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.ProposalParticipant;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProposalParticipantRepository extends JpaRepository<ProposalParticipant, ProposalParticipantId> {

    List<ProposalParticipant> findAllByIdIn(Set<ProposalParticipantId> ids);

    List<ProposalParticipant> findAllByProposal_Id(Long proposalId);

    List<ProposalParticipant> findAllByProposal_IdIn(Set<Long> proposalParticipantIds);

    void deleteAllByIdIn(Set<ProposalParticipantId> ids);

    void deleteAllByProposal_Id(Long proposalId);

    void deleteAllByProposalIdIn(Set<Long> proposalIds);

    List<ProposalParticipant> findAllByStaff_Id(UUID staffId);

    List<ProposalParticipant> findAllByStaff_Role_IdIn(Set<Long> roleId);
}
