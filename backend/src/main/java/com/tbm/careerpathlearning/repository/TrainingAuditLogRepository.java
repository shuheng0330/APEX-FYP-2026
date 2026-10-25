package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingAuditLogRepository extends JpaRepository<TrainingAuditLog, Long> {

    List<TrainingAuditLog> findAllByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(String targetType, String targetId);
}
