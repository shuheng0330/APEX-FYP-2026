package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.service.OrgChartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrgChartServiceImpl implements OrgChartService {

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String ATTRIBUTE_UNIQUE_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String ATTRIBUTE_UNIQUE_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String NAME_ATTRIBUTE = "Name";

    private static final String CREATE_OPERATION = "Create new department";

    private static final String DEPARTMENT_TABLE = "Department";

    @Override
    public List<OrgChartDto> findAllByIsDeletedIsFalse() {
        return orgChartRepository.findAllByIsDeletedIsFalse().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public OrgChartDto getByById(Long id) {
        OrgChart orgChart = orgChartRepository.findById(id).orElseThrow(() ->
                new DataAccessException(
                        messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{DEPARTMENT_TABLE, id.toString()}, Locale.getDefault())
                ));
        return appMapper.toDto(orgChart);
    }

    @Override
    @Transactional
    public OrgChartDto create(OrgChartDto dto) {
        if (dto.getName() == null || dto.getType() == null || dto.getCreatedBy() == null || dto.getUpdatedBy() == null
                || dto.getUpdatedAt() == null || dto.getCreatedAt() == null
                || dto.getName().trim().length() > 255) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<String> existingOrgChartName = orgChartRepository.findAllByIsDeletedIsFalse().stream()
                .map(OrgChart::getName)
                .collect(Collectors.toSet());

        if (existingOrgChartName.contains(dto.getName())) {
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE, new String[]{NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        return appMapper.toDto(orgChartRepository.save(appMapper.toEntity(dto)));

    }

    @Override
    @Transactional
    public List<OrgChartDto> createAndUpdateAll(List<OrgChartDto> dtos) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Map<Long, OrgChartDto> orgChartDtoToBeUpdatedMap = dtos.stream().filter(dto -> dto.getId() != null)
                .collect(Collectors.toMap(OrgChartDto::getId, Function.identity()));
        Set<Long> existingOrgChartIds = orgChartDtoToBeUpdatedMap.keySet();
        Map<Long, OrgChart> existingEntityMap = orgChartRepository.findAllById(existingOrgChartIds).stream()
                .collect(Collectors.toMap(OrgChart::getId, Function.identity()));

        List<OrgChart> entityToBeUpdated = existingEntityMap.values().stream().peek(entity -> {
            OrgChartDto dto = orgChartDtoToBeUpdatedMap.get(entity.getId());

            entity.setName(dto.getName());
            entity.setType(dto.getType());
            entity.setRoot(dto.isRoot());
            entity.setDeleted(dto.isDeleted());
            entity.setUpdatedBy(dto.getUpdatedBy());
            entity.setUpdatedAt(OffsetDateTime.now());

        }).toList();

        List<OrgChartDto> orgChartDtoToBeCreated = dtos.stream().filter(dto -> dto.getId() == null).toList();
        Set<String> existingOrgChartName = orgChartRepository.findAllByIsDeletedIsFalse().stream()
                .map(OrgChart::getName)
                .collect(Collectors.toSet());

        List<OrgChart> entityToBeCreated = orgChartDtoToBeCreated.stream().map(dto -> {

            if (dto.getName() == null || dto.getType() == null || dto.getCreatedBy() == null || dto.getUpdatedBy() == null
                    || dto.getUpdatedAt() == null || dto.getCreatedAt() == null
                    || dto.getName().trim().length() > 255) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (existingOrgChartName.contains(dto.getName())) {
                String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault());
                String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE, new String[]{NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorTitle, errorMessage);
            }

            return appMapper.toEntity(dto);
        }).toList();

        List<OrgChart> entities = new ArrayList<>(entityToBeCreated);
        entities.addAll(entityToBeUpdated);

        return orgChartRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrgChartDto update(Long id, OrgChartDto dto) {
        if (id == null || dto.getName() == null || dto.getType() == null || dto.getUpdatedBy() == null || dto.getUpdatedAt() == null
                || dto.getName().trim().length() > 255) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        OrgChart entity = appMapper.toEntity(this.getByById(id));
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setRoot(dto.isRoot());
        entity.setDeleted(dto.isDeleted());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setUpdatedBy(dto.getUpdatedBy());
        return appMapper.toDto(orgChartRepository.save(entity));
    }

    @Transactional
    @Override
    public void deleteById(Long id, UUID userUUID, OffsetDateTime now) {
        if (id == null) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        OrgChart orgChart = appMapper.toEntity(this.getByById(id));
        orgChart.setDeleted(true);
        orgChart.setUpdatedAt(now);
        orgChart.setUpdatedBy(userUUID);

        orgChartRepository.save(orgChart);
    }

    @Override
    @Transactional
    public void deleteAllByIdIn(Set<Long> ids, UUID userUUID, OffsetDateTime now) {
        List<OrgChart> toDelete = orgChartRepository.findAllById(ids).stream()
                .peek(entity -> {
                    entity.setDeleted(true);
                    entity.setUpdatedAt(now);
                    entity.setUpdatedBy(userUUID);

                })
                .toList();

        orgChartRepository.saveAll(toDelete);
    }

    @Override
    public List<OrgChartDto> getDepartments() {
        return orgChartRepository.findAllByOrgChartTypeD().stream().map(appMapper::toDto).collect(Collectors.toList());
    }
}
