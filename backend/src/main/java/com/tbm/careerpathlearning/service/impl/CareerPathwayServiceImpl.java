package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CareerPathwayDto;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CareerPathway;
import com.tbm.careerpathlearning.repository.CareerPathwayRepository;
import com.tbm.careerpathlearning.service.CareerPathwayService;
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
public class CareerPathwayServiceImpl implements CareerPathwayService {

    @Autowired
    private CareerPathwayRepository careerPathwayRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_CAREER_PATHWAY = "Create Career Pathway";

    private static final String UPDATE_CAREER_PATHWAY = "Update Career Pathway";

    private static final String ATTRIBUTE_UNIQUE_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String ATTRIBUTE_UNIQUE_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String NAME_ATTRIBUTE = "Career Pathway Name";

    @Override
    public List<CareerPathwayDto> getAll() {
        return careerPathwayRepository.findAll().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayDto> getAllByIsDeletedIsFalse() {
        return careerPathwayRepository.findAllByIsDeletedIsFalse().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayDto> getAllByIsDeletedIsFalseAndIdIn(Set<Long> careerPathwayIds) {
        return careerPathwayRepository.findAllByIsDeletedIsFalseAndIdIn(careerPathwayIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public CareerPathwayDto getById(Long id) {
        return careerPathwayRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE,
                                null, Locale.getDefault())));
    }

    @Override
    @Transactional
    public CareerPathwayDto create(CareerPathwayDto dto) {
        if (dto.getName().isEmpty() || dto.getOrgChart() == null
                || dto.getName().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (!Objects.equals(dto.getOrgChart().getId(), dto.getRootRole().getOrgChart().getId())) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        List<CareerPathway> existingCareerPathway = careerPathwayRepository.findAllByIsDeletedIsFalse().stream().toList();
        Set<String> existingCareerPathwayName = existingCareerPathway.stream()
                .map(careerPathway -> careerPathway.getName().toLowerCase()).collect(Collectors.toSet());

        if (existingCareerPathwayName.contains(dto.getName().trim().toLowerCase())) {
            Set<Long> duplicatedOrgChart = existingCareerPathway.stream().filter(careerPathway ->
                            careerPathway.getName().equalsIgnoreCase(dto.getName().trim().toLowerCase()))
                    .map(careerPathway -> careerPathway.getOrgChart().getId()).collect(Collectors.toSet());

            if (!duplicatedOrgChart.isEmpty() && duplicatedOrgChart.contains(dto.getOrgChart().getId())) {
                String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault());
                String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                        new String[]{NAME_ATTRIBUTE, CREATE_CAREER_PATHWAY}, Locale.getDefault());

                throw new BadRequestException(errorTitle, errorMessage);
            }
        }

        dto.setName(dto.getName().trim());
        dto.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());

        return appMapper.toDto(careerPathwayRepository.save(appMapper.toEntity(dto)));
    }


    @Override
    @Transactional
    public CareerPathwayDto update(Long id, CareerPathwayDto dto) {
        CareerPathwayDto existedRecord = this.getById(id);

        if (dto.getName().isEmpty() || dto.getOrgChart() == null
                || dto.getName().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (!Objects.equals(dto.getOrgChart().getId(), dto.getRootRole().getOrgChart().getId())) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        List<CareerPathway> existingCareerPathway = careerPathwayRepository.findAllByIsDeletedIsFalse().stream().toList();

        List<CareerPathway> duplicatedName = existingCareerPathway.stream().filter(careerPathway ->
                        careerPathway.getName().equalsIgnoreCase(dto.getName().trim().toLowerCase())
                                && Objects.equals(careerPathway.getOrgChart().getId(), dto.getOrgChart().getId())
                                && !Objects.equals(careerPathway.getId(), dto.getId()))
                .toList();

        if (!duplicatedName.isEmpty()) {
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                    new String[]{UPDATE_CAREER_PATHWAY}, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        existedRecord.setName(dto.getName().trim());
        existedRecord.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());
        existedRecord.setRootRole(dto.getRootRole());
        existedRecord.setOrgChart(dto.getOrgChart());

        existedRecord.setDeleted(dto.isDeleted());
        existedRecord.setUpdatedBy(dto.getUpdatedBy());
        existedRecord.setUpdatedAt(dto.getUpdatedAt());

        return appMapper.toDto(careerPathwayRepository.save(appMapper.toEntity(existedRecord)));
    }

    @Transactional
    @Override
    public List<CareerPathwayDto> createAndUpdateAll(List<CareerPathwayDto> dtos) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Map<Long, Set<String>> nameSeenWithinDepartment = new HashMap<>();
        Map<Long, Set<String>> nameDuplicates = new HashMap<>();

        List<CareerPathwayDto> allCareerPathwayDto = this.getAllByIsDeletedIsFalse();
        Map<Long, CareerPathwayDto> existingCareerPathwayIdMap = allCareerPathwayDto.stream().collect(Collectors.toMap(
                CareerPathwayDto::getId,
                Function.identity()
        ));
        Set<String> existingCareerPathwayNameMap = allCareerPathwayDto.stream()
                .filter(dto -> dto.getName() != null)
                .map(dto -> dto.getName().toLowerCase().trim())
                .collect(Collectors.toSet());

        List<CareerPathway> entities = dtos.stream().map(dto -> {
            if (dto.getOrgChart() == null || validationService.isNullOrBlank(dto.getName()) || dto.getRootRole() == null
                    || dto.getName().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            CareerPathwayDto careerPathwayDto;
            if (dto.getId() != null) {
                if (existingCareerPathwayIdMap.get(dto.getId()) == null) {
                    throw new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE,
                            null, Locale.getDefault()));
                }

                careerPathwayDto = existingCareerPathwayIdMap.get(dto.getId());
            } else {
                careerPathwayDto = dto;
                careerPathwayDto.setCreatedAt(dto.getCreatedAt());
                careerPathwayDto.setCreatedBy(dto.getCreatedBy());
            }

            careerPathwayDto.setOrgChart(dto.getOrgChart());
            careerPathwayDto.setRootRole(dto.getRootRole());

            if (!Objects.equals(dto.getOrgChart().getId(), dto.getRootRole().getOrgChart().getId())) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_CAREER_PATHWAY}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            Long orgChartId = dto.getOrgChart().getId();
            String name = dto.getName().trim().toLowerCase();

            if (!dto.isDeleted()) {
                if (!nameSeenWithinDepartment
                        .computeIfAbsent(orgChartId, k -> new HashSet<>())
                        .add(name)) {
                    nameDuplicates
                            .computeIfAbsent(orgChartId, k -> new HashSet<>())
                            .add(name);
                }

                if (!nameDuplicates.isEmpty()) {
                    String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault());
                    String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                            new String[]{NAME_ATTRIBUTE, CREATE_CAREER_PATHWAY}, Locale.getDefault());

                    throw new BadRequestException(errorTitle, errorMessage);
                }

                if (existingCareerPathwayNameMap.contains(dto.getName().toLowerCase().trim())) {
                    CareerPathwayDto existingRecord = allCareerPathwayDto.stream().filter(careerPathway ->
                                    Objects.equals(careerPathway.getOrgChart().getId(), dto.getOrgChart().getId())
                                            && careerPathway.getName().equalsIgnoreCase(dto.getName().trim())
                                            && !Objects.equals(careerPathway.getId(), dto.getId()))
                            .findAny().orElse(null);

                    if (existingRecord != null) { //from the same department
                        String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault());
                        String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                                new String[]{NAME_ATTRIBUTE, CREATE_CAREER_PATHWAY}, Locale.getDefault());

                        throw new BadRequestException(errorTitle, errorMessage);
                    }
                }

                if (dto.getId() != null) {
                    String existingName = existingCareerPathwayIdMap.get(dto.getId()).getName().toLowerCase().trim();
                    if (!dto.getName().equalsIgnoreCase(existingName)) {
                        existingCareerPathwayNameMap.remove(existingName);
                        existingCareerPathwayNameMap.add(dto.getName().toLowerCase().trim());
                    }
                }
            } else {
                if (dto.getId() != null) {
                    String existingName = existingCareerPathwayIdMap.get(dto.getId()).getName().toLowerCase().trim();
                    existingCareerPathwayNameMap.remove(existingName);
                }
            }

            careerPathwayDto.setName(dto.getName());
            careerPathwayDto.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());
            careerPathwayDto.setDeleted(dto.isDeleted());

            careerPathwayDto.setUpdatedBy(dto.getUpdatedBy());
            careerPathwayDto.setUpdatedAt(dto.getUpdatedAt());

            return appMapper.toEntity(careerPathwayDto);
        }).toList();

        return careerPathwayRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id, UUID userUUID, OffsetDateTime now) {
        CareerPathwayDto toDelete = this.getById(id);
        toDelete.setDeleted(true);
        toDelete.setUpdatedBy(userUUID);
        toDelete.setUpdatedAt(now);

        careerPathwayRepository.save(appMapper.toEntity(toDelete));
    }

    @Override
    @Transactional
    public void deleteAll(Set<Long> ids, UUID userUUID, OffsetDateTime now) {
        List<CareerPathway> toDelete = careerPathwayRepository.findAllById(ids).stream().peek(dto -> {
                    dto.setDeleted(true);
                    dto.setUpdatedAt(now);
                    dto.setUpdatedBy(userUUID);
                })
                .toList();

        careerPathwayRepository.saveAll(toDelete);
    }
}
