package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.JobScopeDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.JobScope;
import com.tbm.careerpathlearning.repository.JobScopeRepository;
import com.tbm.careerpathlearning.service.JobScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobScopeServiceImpl implements JobScopeService {

    @Autowired
    private JobScopeRepository jobScopeRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AppMapper appMapper;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    @Override
    public List<JobScopeDto> findAllByIsDeletedIsFalse() {
        return jobScopeRepository.findAllByIsDeletedIsFalse().stream()
                .map(this.appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<JobScopeDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids) {
        List<JobScopeDto> jobScopeList = jobScopeRepository.findAllByIsDeletedIsFalseAndIdIn(ids).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());

        if (ids.size() != jobScopeList.size()) {
            throw new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault()));
        }

        return jobScopeList;
    }

    @Override
    public List<JobScopeDto> findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(Set<String> jobScopes) {
        jobScopes = jobScopes.stream().map(String::toLowerCase).collect(Collectors.toSet());
        return jobScopeRepository.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(jobScopes).stream().map(this.appMapper::toDto).toList();
    }

    @Transactional
    @Override
    public List<JobScopeDto> createAll(List<JobScopeDto> jobScopeDtoList) {
        // normalize and deduplicate incoming list
        Map<String, JobScopeDto> normalized = jobScopeDtoList.stream()
                .collect(Collectors.toMap(
                        dto -> {

                            if (dto.getJobScope() == null || dto.getJobScope().trim().length() > 1000) {
                                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, null, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }

                            return dto.getJobScope().trim().toLowerCase();
                        },
                        dto -> dto,
                        (first, second) -> first // keep first if duplicate
                ));

        Set<String> jobScopes = normalized.keySet();

        List<JobScopeDto> existingJobScopeDto = this.findAllByIsDeletedIsFalseAndJobScopeIgnoreCaseIn(jobScopes);

        Set<String> existingJobScope = existingJobScopeDto.stream()
                .map(jobScopeDto -> jobScopeDto.getJobScope().trim().toLowerCase())
                .collect(Collectors.toSet());

        List<JobScopeDto> toCreate = normalized.entrySet().stream()
                .filter(entry -> !existingJobScope.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        if (toCreate.isEmpty()) {
            return existingJobScopeDto; // everything already existed
        }

        List<JobScope> entities = toCreate.stream()
                .map(appMapper::toEntity)
                .toList();

        List<JobScopeDto> created = jobScopeRepository.saveAll(entities).stream()
                .map(appMapper::toDto)
                .toList();

        List<JobScopeDto> result = new ArrayList<>(existingJobScopeDto);
        result.addAll(created);
        return result;
    }


    @Transactional
    @Override
    public void deleteAllByIdIn(Set<Long> ids, UUID userId) {
        List<JobScopeDto> jobScopeDtoList = this.findAllByIsDeletedIsFalseAndIdIn(ids);

        jobScopeDtoList.forEach(jobScopeDto -> {
            jobScopeDto.setDeleted(true);
            jobScopeDto.setUpdatedBy(userId);
            jobScopeDto.setUpdatedAt(OffsetDateTime.now());
        });

        jobScopeRepository.saveAll(jobScopeDtoList.stream().map(appMapper::toEntity).collect(Collectors.toList()));
    }
}


