package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findAllByOrgChartIdInAndIsDeletedIsFalse(Set<Long> orgChartIds);

    List<Role> findAllByIsDeletedIsFalse();

    List<Role> findAllByIdInAndIsDeletedIsFalse(Set<Long> ids);

    @Query("SELECT r FROM Role r JOIN FETCH r.orgChart o WHERE r.isDeleted = false AND r.isVisible = true AND o.type = 'D'")
    List<Role> findAllVisibleAndNotDeletedWithOrgChartTypeD();

    Optional<Role> findAllByIsDeletedIsFalseAndOrgChart_IdAndNameIgnoreCase(Long orgChartId, String name);

    void deleteAllByIdIn(Set<Long> ids);
}
