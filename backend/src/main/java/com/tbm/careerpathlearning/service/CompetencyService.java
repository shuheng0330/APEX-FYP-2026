package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CompetencyDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CompetencyService {

    List<CompetencyDto> findAllByIsDeletedIsFalse();

    CompetencyDto findAllById(Long id);

    Optional<CompetencyDto> findByNameIgnoreCaseAndIsDeletedIsFalse(String competencyName);

    List<CompetencyDto> createAndUpdateAll(List<CompetencyDto> dtos);

    List<CompetencyDto> findAllByIsDeletedIsFalseAndNameIgnoreCaseIn(Set<String> competencyNames);

    CompetencyDto create(CompetencyDto competencyDto);

    List<CompetencyDto> createAll(List<CompetencyDto> dtos);

    CompetencyDto update(Long id, CompetencyDto competencyDto);

    List<CompetencyDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> competencyIds);

    void delete(Long id, UUID userId);

    void deleteAllByIdIn(Set<Long> competencyIds, UUID userId);

    void checkRedundancyByName(Long competencyId, String competencyName);
}
