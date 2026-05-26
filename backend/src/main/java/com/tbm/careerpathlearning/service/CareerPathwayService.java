package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CareerPathwayDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CareerPathwayService {
    List<CareerPathwayDto> getAll();

    List<CareerPathwayDto> getAllByIsDeletedIsFalse();

    List<CareerPathwayDto> getAllByIsDeletedIsFalseAndIdIn(Set<Long> id);

    CareerPathwayDto getById(Long id);

    CareerPathwayDto create(CareerPathwayDto CareerPathwayDto);

    CareerPathwayDto update(Long id, CareerPathwayDto CareerPathwayDto);

    List<CareerPathwayDto> createAndUpdateAll(List<CareerPathwayDto> dtos);

    void delete(Long id, UUID userUUID, OffsetDateTime now);

    void deleteAll(Set<Long> ids, UUID userUUID, OffsetDateTime now);

}
