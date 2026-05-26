package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompetencyCompTag;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import com.tbm.careerpathlearning.repository.CompetencyCompTagRepository;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompetencyCompTagServiceImpl implements CompetencyCompTagService {


    @Autowired
    private CompetencyService competencyService;

    @Autowired
    private CompTagService compTagService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private CompetencyCompTagRepository competencyCompTagRepository;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String ASSIGN_COMP_TAG = "Assigning tags to the competency";

    @Override
    public List<CompetencyCompTagDto> findAll() {
        return competencyCompTagRepository.findAll().stream().map(this.appMapper::toDto).toList();
    }

    @Override
    public List<CompetencyCompTagDto> findAllByIdIn(Set<CompetencyCompTagId> competencyCompTagIds) {
        return competencyCompTagRepository.findAllById(competencyCompTagIds).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagDto> findAllByCompetencyId(Long competencyId) {
        return competencyCompTagRepository.findAllByCompetency_Id(competencyId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagDto> findAllByCompetencyIdIn(Set<Long> competencyIds) {
        return competencyCompTagRepository.findAllByCompetency_IdIn(competencyIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagDto> findAllByCompTagIdIn(Set<Long> compTagIds) {
        return competencyCompTagRepository.findAllByCompTag_IdIn(compTagIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagDto> findAllUsedByOther(Set<Long> compTagIds, Long competencyId) {
        return competencyCompTagRepository.findAllByCompTag_IdInAndCompetency_IdNot(compTagIds, competencyId).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompetencyCompTagDto> findAllUsedByOthers(Set<Long> compTagIds, Set<Long> competencyIds) {
        return competencyCompTagRepository.findAllByCompTag_IdInAndCompetency_IdNotIn(compTagIds, competencyIds).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<CompetencyCompTagDto> createAll(List<CompetencyCompTagDto> competencyCompTagDtoList) {
        if (competencyCompTagDtoList == null || competencyCompTagDtoList.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{ASSIGN_COMP_TAG}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> competencyIds = competencyCompTagDtoList.stream()
                .map(dto -> dto.getId().getCompetencyId()).collect(Collectors.toSet());

        Set<Long> compTagIds = competencyCompTagDtoList.stream()
                .map(dto -> dto.getId().getCompTagId()).collect(Collectors.toSet());

        Set<CompetencyCompTagId> competencyCompTagIds = competencyCompTagDtoList.stream()
                .map(CompetencyCompTagDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompetencyIds = competencyService.findAllByIsDeletedIsFalseAndIdIn(competencyIds).stream()
                .map(CompetencyDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompTagIds = compTagService.findAllByIsDeletedIsFalseAndIdIn(compTagIds)
                .stream().map(CompTagDto::getId).collect(Collectors.toSet());

        Set<CompetencyCompTagId> existingCompetencyCompTagIds = this.findAllByIdIn(competencyCompTagIds).stream()
                .map(CompetencyCompTagDto::getId).collect(Collectors.toSet());

        for (CompetencyCompTagDto dto : competencyCompTagDtoList) {
            Long competencyId = dto.getId().getCompetencyId();
            Long compTagId = dto.getId().getCompTagId();

            if (!existingCompetencyIds.contains(competencyId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingCompTagIds.contains(compTagId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingCompetencyCompTagIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null,
                        Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }

        List<CompetencyCompTag> entities = competencyCompTagDtoList.stream()
                .map(this.appMapper::toEntity).collect(Collectors.toList());

        return competencyCompTagRepository.saveAll(entities).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }


    @Transactional
    @Override
    public void deleteByCompetencyIdAndCompTagIdIn(Long competencyId, Set<Long> compTagIds) {
        competencyCompTagRepository.deleteAllByCompetency_IdAndCompTag_IdIn(competencyId, compTagIds);
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<CompetencyCompTagId> competencyCompTagIds) {
        competencyCompTagRepository.deleteAllById(competencyCompTagIds);
    }
}
