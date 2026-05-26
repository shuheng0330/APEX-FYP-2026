package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.RoleJobScopeDto;
import com.tbm.careerpathlearning.model.RoleJobScopeId;

import java.util.List;
import java.util.Set;

public interface RoleJobScopeService {
    List<RoleJobScopeDto> findAll();

    List<RoleJobScopeDto> findAllByIdIn(Set<RoleJobScopeId> roleJobScopeIds);

    List<RoleJobScopeDto> createAll(List<RoleJobScopeDto> roleJobScopeDtoList);

    List<RoleJobScopeDto> findAllByRoleId(Long roleId);

    List<RoleJobScopeDto> findAllByRoleIdIn(Set<Long> roleIds);

    List<RoleJobScopeDto> findAllByJobScopeIdIn(Set<Long> jobScopeIds);

    List<RoleJobScopeDto> findJobScopesUsedByOtherRoles(Set<Long> jobScopeIds, Long roleId);

    List<RoleJobScopeDto> findJobScopesUsedByRoleIdNotIn(Set<Long> jobScopeIds, Set<Long> roleIds);

    void deleteByRoleIdAndJobScopeIdIn(Long roleId, Set<Long> jobScopeIds);

    void deleteAllByIdIn(Set<RoleJobScopeId> roleJobScopeIds);

    List<RoleJobScopeDto> findAndDeleteAllByRoleId(Long roleId);

    List<RoleJobScopeDto> findAndDeleteAllByRoleIdIn(Set<Long> roleIds);
}
