package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SopModuleRepository extends JpaRepository<SopModule, Long> {
    List<SopModule> findAllBySopDocumentIdOrderByModuleOrderAsc(Long sopDocumentId);

    void deleteAllBySopDocumentId(Long sopDocumentId);
}
