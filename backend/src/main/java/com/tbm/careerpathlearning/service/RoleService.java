package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.dto.RoleDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleService {
    List<RoleDto> getAll();

    List<RoleDto> getAllByDeletedIsFalse();

    RoleDto getAllById(Long id);

    List<RoleDto> getAllByIsDeletedIsFalseAndIdIn(Set<Long> ids);

    RoleDto create(RoleDto roleDto);

    List<RoleDto> createAll(List<RoleDto> dtos);

    RoleDto update(Long id, RoleDto roleDto);

    List<RoleDto> updateAll(Set<Long> roleIds, List<RoleDto> roleDtos);

    List<RoleDto> createAndUpdateAll(List<RoleDto> dtos);

    void delete(Long id, UUID userId);

    void deleteAllByRoleIdIn(Set<Long> roleIds, UUID userId);

    List<RoleDto> findAndDeleteAllByOrgChartIdIn(Set<Long> orgChartIds, UUID userId, OffsetDateTime now);
}
