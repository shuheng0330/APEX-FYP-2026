package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CompTagDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CompTagService {

    List<CompTagDto> findAllByIsDeletedIsFalse();

    List<CompTagDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids);

    List<CompTagDto> findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(Set<String> tags);

    List<CompTagDto> createAll(List<CompTagDto> compTagDtoList);

    void deleteAllByIdIn(Set<Long> ids, UUID userId);
}
