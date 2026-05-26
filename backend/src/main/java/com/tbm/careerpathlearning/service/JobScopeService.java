package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.JobScopeDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface JobScopeService {

    List<JobScopeDto> findAllByIsDeletedIsFalse();

    List<JobScopeDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids);

    List<JobScopeDto> findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(Set<String> jobScopes);

    List<JobScopeDto> createAll(List<JobScopeDto> jobScopeDtoList);

    void deleteAllByIdIn(Set<Long> ids, UUID userId);
}
