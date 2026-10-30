package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiCentralResultEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface KpiCentralResultEvidenceRepository extends JpaRepository<KpiCentralResultEvidence,Long> {
    List<KpiCentralResultEvidence> findAllByItemIdOrderByUploadedAtAscIdAsc(Long itemId);
    List<KpiCentralResultEvidence> findAllByItemIdInOrderByUploadedAtAscIdAsc(Collection<Long> itemIds);
}
