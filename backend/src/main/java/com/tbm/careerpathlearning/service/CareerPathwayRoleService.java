package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CareerPathwayRoleDto;
import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

public interface CareerPathwayRoleService {
    List<CareerPathwayRoleDto> getAll();

    List<CareerPathwayRoleDto> getAllByIdIn(Set<CareerPathwayRoleId> ids);

    List<CareerPathwayRoleDto> getAllByCareerPathwayId(Long careerPathwayId);

    List<CareerPathwayRoleDto> createAll(List<CareerPathwayRoleDto> dtos);

    @Transactional
    void deleteAllByIdIn(Set<CareerPathwayRoleId> ids);

    @Transactional
    void deleteByCareerPathwayId(Long careerPathwayId);

    @Transactional
    void deleteAllByCareerPathwayIdIn(Set<Long> careerPathwayIds);
}
