package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.RoleCompetencyId;

import java.util.List;
import java.util.Set;

public interface RoleCompetencyService {

    List<RoleCompetencyDto> findAll();

    List<RoleCompetencyDto> findAllByIdIn(Set<RoleCompetencyId> roleCompetencyIds);

    List<RoleCompetencyDto> createAll(List<RoleCompetencyDto> roleCompetencyDtos);

    List<RoleCompetencyDto> updateAll(Set<RoleCompetencyId> roleCompetencyIds, List<RoleCompetencyDto> roleCompetencyDtos);

    List<RoleCompetencyDto> findAllByRoleId(Long roleId);

    void deleteAllByIdIn(Set<RoleCompetencyId> roleCompetencyIds);

    void deleteAllByRoleId(Long roleId);

    void deleteAllByRoleIdIn(Set<Long> roleId);

}
