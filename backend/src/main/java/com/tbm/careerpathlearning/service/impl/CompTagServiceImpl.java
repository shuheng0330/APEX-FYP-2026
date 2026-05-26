package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CompTagDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CompTag;
import com.tbm.careerpathlearning.repository.CompTagRepository;
import com.tbm.careerpathlearning.service.CompTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompTagServiceImpl implements CompTagService {

    @Autowired
    private CompTagRepository compTagRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UPDATE_OPERATION = "Update Competency Tag";

    @Override
    public List<CompTagDto> findAllByIsDeletedIsFalse() {
        return compTagRepository.findAllByIsDeletedIsFalse().stream()
                .map(this.appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompTagDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids) {
        return compTagRepository.findAllByIsDeletedIsFalseAndIdIn(ids).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CompTagDto> findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(Set<String> compTags) {
        compTags = compTags.stream().map(String::toLowerCase).collect(Collectors.toSet());
        return compTagRepository.findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(compTags).stream().map(this.appMapper::toDto).toList();
    }

    @Transactional
    @Override
    public List<CompTagDto> createAll(List<CompTagDto> compTagDtoList) {
        // normalize and deduplicate incoming list
        Map<String, CompTagDto> normalized = compTagDtoList.stream()
                .peek(dto-> {
                    if (dto.getTag() == null ||
                            dto.getTag().trim().length() > 100) {
                        String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                                new String[]{UPDATE_OPERATION}, Locale.getDefault());

                        throw new BadRequestException(errorMessage);
                    }
                })
                .collect(Collectors.toMap(
                        dto -> dto.getTag().trim().toLowerCase(),
                        dto -> dto,
                        (first, second) -> first // keep first if duplicate
                ));

        Set<String> tags = normalized.keySet();

        List<CompTagDto> existingCompTagDto = this.findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(tags);

        Set<String> existingTag = existingCompTagDto.stream()
                .map(tag -> tag.getTag().trim().toLowerCase())
                .collect(Collectors.toSet());

        List<CompTagDto> toCreate = normalized.entrySet().stream()
                .filter(entry -> !existingTag.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        if (toCreate.isEmpty()) {
            return existingCompTagDto; // everything already existed
        }

        List<CompTag> entities = toCreate.stream()
                .map(appMapper::toEntity)
                .toList();

        List<CompTagDto> created = compTagRepository.saveAll(entities).stream()
                .map(appMapper::toDto)
                .toList();

        List<CompTagDto> result = new ArrayList<>(existingCompTagDto);
        result.addAll(created);
        return result;
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<Long> ids, UUID userId) {
        List<CompTagDto> compTagDtoList = this.findAllByIsDeletedIsFalseAndIdIn(ids);

        compTagDtoList.forEach(compTagDto -> {
            compTagDto.setDeleted(true);
            compTagDto.setUpdatedBy(userId);
            compTagDto.setUpdatedAt(OffsetDateTime.now());
        });

        compTagRepository.saveAll(compTagDtoList.stream().map(appMapper::toEntity).collect(Collectors.toList()));
    }
}


