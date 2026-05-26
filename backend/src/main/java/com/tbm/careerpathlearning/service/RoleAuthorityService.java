package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.RoleAuthorityId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface RoleAuthorityService {
    List<RoleAuthorityDto> getAll();

    List<RoleAuthorityDto> getAllByIdIn(Set<RoleAuthorityId> roleAuthorityId);

    Optional<RoleAuthorityDto> findById(RoleAuthorityId roleAuthorityId);

    List<RoleAuthorityDto> getAllByRoleId(Long roleId);

    List<RoleAuthorityDto> getAllByRoleIdIn(Set<Long> roleIds);

    List<RoleDto> getDistinctRole();

    Map<Long, Boolean> getRoleAuthorityMapByRoleId(Long roleId);

    RoleAuthorityDto create(RoleAuthorityDto roleAuthorityDto);

    List<RoleAuthorityDto> createAll(List<RoleAuthorityDto> roleAuthorityDtoList);

    void deleteById(RoleAuthorityId roleAuthorityId);

    void deleteAllByIdIn(Set<RoleAuthorityId> roleAuthorityIds);

    void deleteAllByRoleIdIn(Set<Long> roleIds);

    void deleteAllByRoleId(Long roleId);

    void deleteByRoleIdAndAuthorityIdIn(Long roleId, Set<Long> authorityIds);
}
