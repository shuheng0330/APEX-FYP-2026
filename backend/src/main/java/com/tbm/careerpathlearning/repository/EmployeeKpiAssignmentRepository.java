package com.tbm.careerpathlearning.repository;
import com.tbm.careerpathlearning.model.EmployeeKpiAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmployeeKpiAssignmentRepository extends JpaRepository<EmployeeKpiAssignment,Long> {
    List<EmployeeKpiAssignment> findAllByParticipantId(Long participantId);
}
