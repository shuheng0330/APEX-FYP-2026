package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CompetencyDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Competency;
import com.tbm.careerpathlearning.repository.CompetencyRepository;
import com.tbm.careerpathlearning.service.CompetencyService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompetencyServiceImpl implements CompetencyService {

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String ATTRIBUTE_UNIQUE_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String ATTRIBUTE_UNIQUE_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String COMPETENCY_NAME_ATTRIBUTE = "Name";

    private static final String CREATE_OPERATION = "Competency Creation";

    private static final String UPDATE_OPERATION = "Update Competency";

    @Override
    public List<CompetencyDto> findAllByIsDeletedIsFalse() {
        return competencyRepository.findAllByIsDeletedIsFalse().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public CompetencyDto findAllById(Long id) {
        Competency competency = competencyRepository.findById(id)
                .orElseThrow(() -> new DataAccessException(
                        messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())
                ));
        return appMapper.toDto(competency);
    }

    @Override
    public List<CompetencyDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> competencyIds) {
        List<CompetencyDto> competencyDtoList = competencyRepository.findAllByIsDeletedIsFalseAndIdIn(competencyIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());

        if (competencyIds.size() != competencyDtoList.size()) {
            throw new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault()));
        }

        return competencyDtoList;
    }

    @Override
    public Optional<CompetencyDto> findByNameIgnoreCaseAndIsDeletedIsFalse(String competencyName) {
        return this.competencyRepository.findByNameIgnoreCaseAndIsDeletedIsFalse(competencyName)
                .map(appMapper::toDto);
    }

    @Transactional
    @Override
    public CompetencyDto create(CompetencyDto dto) {
        if (validationService.isNullOrBlank(dto.getName())
                || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        this.checkRedundancyByName(null, dto.getName());

        return appMapper.toDto(competencyRepository.save(appMapper.toEntity(dto)));
    }

    @Transactional
    @Override
    public List<CompetencyDto> createAll(List<CompetencyDto> dtos) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<String> nameSeen = new HashSet<>();
        Set<String> nameDuplicates = dtos.stream()
                .map(CompetencyDto::getName)
                .filter(Objects::nonNull)
                .filter(name -> !nameSeen.add(name))
                .collect(Collectors.toSet());

        if (!nameDuplicates.isEmpty()) { // incoming names duplicated
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{COMPETENCY_NAME_ATTRIBUTE}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                    new String[]{COMPETENCY_NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        Map<String, CompetencyDto> dtoMap = dtos.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getName().trim(),
                        dto -> dto
                ));

        Set<String> competencyNames = dtoMap.keySet();

        List<CompetencyDto> existingCompetencyDto = this.findAllByIsDeletedIsFalseAndNameIgnoreCaseIn(competencyNames);

        if (!existingCompetencyDto.isEmpty()) { // incoming data redundant with existing data
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{COMPETENCY_NAME_ATTRIBUTE}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                    new String[]{COMPETENCY_NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        List<Competency> entities = dtos.stream().map(dto -> {
                    if (validationService.isNullOrBlank(dto.getName())
                            || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                        String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                                new String[]{CREATE_OPERATION}, Locale.getDefault());

                        throw new BadRequestException(errorMessage);
                    }

                    return appMapper.toEntity(dto);
                })
                .toList();

        return competencyRepository.saveAll(entities).stream().map(appMapper::toDto).toList();
    }

    @Transactional
    @Override
    public List<CompetencyDto> createAndUpdateAll(List<CompetencyDto> dtos) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<String> nameSeen = new HashSet<>();
        Set<String> nameDuplicates = dtos.stream()
                .filter(dto -> !dto.isDeleted())
                .map(CompetencyDto::getName)
                .filter(Objects::nonNull)
                .filter(name -> !nameSeen.add(name))
                .collect(Collectors.toSet());

        if (!nameDuplicates.isEmpty()) { // incoming names duplicated
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{COMPETENCY_NAME_ATTRIBUTE}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                    new String[]{COMPETENCY_NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        List<CompetencyDto> existingCompetencyDto = this.findAllByIsDeletedIsFalse();
        Map<Long, CompetencyDto> existingCompetencyIdMap = new HashMap<>(existingCompetencyDto.stream().collect(Collectors.toMap(
                CompetencyDto::getId,
                Function.identity()
        )));

        Map<String, CompetencyDto> existingCompetencyNameMap = new HashMap<>(existingCompetencyDto.stream()
                .filter(c -> c.getName() != null)
                .collect(Collectors.toMap(
                        competency -> competency.getName().toLowerCase().trim(),
                        Function.identity(),
                        (existing, replacement) -> existing
                )));

        List<Competency> entities = dtos.stream().map(dto -> {
                    if (validationService.isNullOrBlank(dto.getName())
                            || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                        String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                                new String[]{UPDATE_OPERATION}, Locale.getDefault());

                        throw new BadRequestException(errorMessage);
                    }

                    CompetencyDto competencyDto;
                    if (dto.getId() != null) {
                        if (existingCompetencyIdMap.get(dto.getId()) == null) {
                            throw new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault()));
                        }

                        competencyDto = existingCompetencyIdMap.get(dto.getId());
                    } else {
                        competencyDto = dto;
                        competencyDto.setCreatedAt(dto.getCreatedAt());
                        competencyDto.setCreatedBy(dto.getCreatedBy());
                    }

                    if (!dto.isDeleted()) {
                        if (existingCompetencyNameMap.containsKey(dto.getName().toLowerCase().trim())) {
                            CompetencyDto existingCompetency = existingCompetencyNameMap.get(dto.getName().toLowerCase().trim());
                            if (competencyDto.getId() == null || !Objects.equals(competencyDto.getId(), existingCompetency.getId())) {
                                String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{COMPETENCY_NAME_ATTRIBUTE}, Locale.getDefault());
                                String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                                        new String[]{COMPETENCY_NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault());

                                throw new BadRequestException(errorTitle, errorMessage);
                            }
                        }

                        if (dto.getId() != null) {
                            String existingName = existingCompetencyIdMap.get(dto.getId()).getName() == null ? null :
                                    existingCompetencyIdMap.get(dto.getId()).getName().toLowerCase().trim();
                            if (!dto.getName().equalsIgnoreCase(existingName)) {
                                existingCompetencyNameMap.remove(existingName);
                                existingCompetencyNameMap.put(dto.getName().toLowerCase().trim(), competencyDto);
                            }
                        }
                    } else {
                        if (dto.getId() != null) {
                            String existingName = existingCompetencyIdMap.get(dto.getId()).getName() == null ? null :
                                    existingCompetencyIdMap.get(dto.getId()).getName().toLowerCase().trim();
                            existingCompetencyNameMap.remove(existingName);
                        }
                    }

                    competencyDto.setName(dto.getName().trim());
                    competencyDto.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());
                    competencyDto.setDeleted(dto.isDeleted());

                    competencyDto.setUpdatedBy(dto.getUpdatedBy());
                    competencyDto.setUpdatedAt(dto.getUpdatedAt());

                    return appMapper.toEntity(competencyDto);
                })
                .toList();

        return competencyRepository.saveAll(entities).stream().map(appMapper::toDto).toList();
    }

    @Override
    public List<CompetencyDto> findAllByIsDeletedIsFalseAndNameIgnoreCaseIn(Set<String> competencyNames) {
        competencyNames = competencyNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        return competencyRepository.findAllByNameInIgnoreCaseAndIsDeletedIsFalse(competencyNames).stream().map(appMapper::toDto).toList();
    }

    @Override
    public void checkRedundancyByName(Long competencyId, String competencyName) {
        Optional<CompetencyDto> existedCompetencyDto = this.findByNameIgnoreCaseAndIsDeletedIsFalse(competencyName);

        if (competencyId == null) {
            if (existedCompetencyDto.isPresent()) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null, Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        } else {
            if (existedCompetencyDto.isPresent() && !existedCompetencyDto.get().getId().equals(competencyId)) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null, Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }
    }

    @Transactional
    @Override
    public CompetencyDto update(Long id, CompetencyDto dto) {
        CompetencyDto competencyToBeUpdated = this.findAllById(id);

        if (validationService.isNullOrBlank(dto.getName())
                || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        this.checkRedundancyByName(id, dto.getName());

        competencyToBeUpdated.setName(dto.getName());
        competencyToBeUpdated.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());
        competencyToBeUpdated.setUpdatedAt(dto.getUpdatedAt());
        competencyToBeUpdated.setUpdatedBy(dto.getUpdatedBy());

        return appMapper.toDto(
                competencyRepository.save(appMapper.toEntity(competencyToBeUpdated))
        );
    }

    @Transactional
    @Override
    public void delete(Long id, UUID userId) {
        CompetencyDto competencyToBeDeleted = this.findAllById(id);

        competencyToBeDeleted.setDeleted(true);
        competencyToBeDeleted.setUpdatedBy(userId);
        competencyToBeDeleted.setUpdatedAt(OffsetDateTime.now());

        competencyRepository.save(appMapper.toEntity(competencyToBeDeleted));
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<Long> competencyIds, UUID userId) {
        List<CompetencyDto> competencyDtoList = this.findAllByIsDeletedIsFalseAndIdIn(competencyIds);

        competencyDtoList.forEach(competencyDto -> {
            competencyDto.setDeleted(true);
            competencyDto.setUpdatedBy(userId);
            competencyDto.setUpdatedAt(OffsetDateTime.now());
        });

        competencyRepository.saveAll(competencyDtoList.stream()
                .map(appMapper::toEntity).collect(Collectors.toList()));
    }
}
