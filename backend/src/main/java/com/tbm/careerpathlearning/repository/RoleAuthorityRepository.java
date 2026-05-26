package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.RoleAuthority;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;


@Repository
public interface RoleAuthorityRepository extends JpaRepository<RoleAuthority, RoleAuthorityId> {

    List<RoleAuthority> findByRoleId(Long roleId);

    List<RoleAuthority> findAllByRoleIdIn(Set<Long> roleIds);

    @Query("SELECT DISTINCT ra.role FROM RoleAuthority ra")
    List<Role> findDistinctRole();

    void deleteAllByRole_IdIn(Set<Long> roleIds);

    void deleteAllByRole_Id(Long roleId);

    void deleteAllByAuthority_IdInAndRole_Id(Set<Long> authorityIds, Long roleId);
}
