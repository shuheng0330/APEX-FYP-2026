package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.EmployeeLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeLevelRepository extends JpaRepository<EmployeeLevel, Long> {
    List<EmployeeLevel> findAllByOrderByDisplayOrderAsc();
    Optional<EmployeeLevel> findByCodeIgnoreCase(String code);
}
