package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CompetencyCompTagDto;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;

import java.util.List;
import java.util.Set;

public interface CompetencyCompTagService {
    List<CompetencyCompTagDto> findAll();

    List<CompetencyCompTagDto> findAllByIdIn(Set<CompetencyCompTagId> competencyCompTagIds);

    List<CompetencyCompTagDto> createAll(List<CompetencyCompTagDto> competencyCompTagDtoList);

    List<CompetencyCompTagDto> findAllByCompetencyId(Long competencyId);

    List<CompetencyCompTagDto> findAllByCompetencyIdIn(Set<Long> competencyIds);

    List<CompetencyCompTagDto> findAllByCompTagIdIn(Set<Long> compTagIds);

    List<CompetencyCompTagDto> findAllUsedByOther(Set<Long> compTagIds, Long competencyId);

    List<CompetencyCompTagDto> findAllUsedByOthers(Set<Long> compTagIds, Set<Long> competencyIds);

    void deleteByCompetencyIdAndCompTagIdIn(Long competencyId, Set<Long> compTagIds);

    void deleteAllByIdIn(Set<CompetencyCompTagId> competencyCompTagIds);
}
