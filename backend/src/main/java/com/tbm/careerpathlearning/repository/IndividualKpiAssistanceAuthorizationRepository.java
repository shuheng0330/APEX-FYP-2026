package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.IndividualKpiAssistanceAuthorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface IndividualKpiAssistanceAuthorizationRepository extends JpaRepository<IndividualKpiAssistanceAuthorization,Long> {
    boolean existsByOwnerParticipantIdAndSuperiorIdAndStatusNot(Long participantId,UUID superiorId,
        com.tbm.careerpathlearning.enums.KpiAssistanceStatus status);
    List<IndividualKpiAssistanceAuthorization> findAllByOrderByRequestedAtDescIdDesc();
    @Query("select a from IndividualKpiAssistanceAuthorization a where a.superior.id=:superiorId "
        +"and a.ownerParticipant.staff.manager.id=:superiorId order by a.requestedAt desc,a.id desc")
    List<IndividualKpiAssistanceAuthorization> findCurrentSuperiorCases(UUID superiorId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from IndividualKpiAssistanceAuthorization a where a.id=:id")
    Optional<IndividualKpiAssistanceAuthorization> lockById(Long id);
}
