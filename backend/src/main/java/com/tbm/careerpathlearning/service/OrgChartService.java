package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrgChartDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OrgChartService {
    List<OrgChartDto> findAllByIsDeletedIsFalse();

    OrgChartDto getByById(Long id);

    OrgChartDto create(OrgChartDto orgChartDto);

    OrgChartDto update(Long id, OrgChartDto roleDto);

    List<OrgChartDto> createAndUpdateAll(List<OrgChartDto> dtos);

    void deleteById(Long id, UUID userUUID, OffsetDateTime now);

    void deleteAllByIdIn(Set<Long> ids, UUID userUUID, OffsetDateTime now);

    List<OrgChartDto> getDepartments();
}
