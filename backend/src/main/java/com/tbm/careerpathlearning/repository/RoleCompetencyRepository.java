package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.RoleCompetency;
import com.tbm.careerpathlearning.model.RoleCompetencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface RoleCompetencyRepository extends JpaRepository<RoleCompetency, Long> {

    List<RoleCompetency> findAllByIdIn(Set<RoleCompetencyId> ids);

    List<RoleCompetency> findByRoleId(Long roleId);

    void deleteAllByIdIn(Set<RoleCompetencyId> ids);

    void deleteAllByRoleId(Long roleId);

    void deleteAllByRoleIdIn(Set<Long> roleIds);

}
