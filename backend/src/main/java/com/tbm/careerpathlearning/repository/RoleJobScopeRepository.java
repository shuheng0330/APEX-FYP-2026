package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;


@Repository
public interface RoleJobScopeRepository extends JpaRepository<RoleJobScope, RoleJobScopeId> {

    List<RoleJobScope> findAllByRole_Id(Long roleId);

    List<RoleJobScope> findAllByRole_IdIn(Set<Long> roleIds);

    List<RoleJobScope> findAllByJobScope_IdIn(Set<Long> jobScopeIds);

    //@Query("SELECT rj FROM RoleJobScope rj WHERE rj.jobScope.id IN :jobScopeIds AND rj.role.id != :roleId")
    List<RoleJobScope> findAllByJobScope_IdInAndRole_IdNot(Set<Long> jobScopeIds, Long roleId);

    //@Query("SELECT rj FROM RoleJobScope rj WHERE rj.jobScope.id IN :jobScopeIds AND rj.role.id NOT IN (:roleIds)")
    List<RoleJobScope> findAllByJobScope_IdInAndRole_IdNotIn(Set<Long> jobScopeIds, Set<Long> roleIds);

    void deleteAllByRole_IdAndJobScope_IdIn(Long role_id, Set<Long> jobScope_id);
}
