package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.RoleCompetencyRepository;
import com.tbm.careerpathlearning.service.CompetencyService;
import com.tbm.careerpathlearning.service.RoleCompetencyService;
import com.tbm.careerpathlearning.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleCompetencyServiceImpl implements RoleCompetencyService {

    @Autowired
    private RoleService roleService;

    @Autowired
    private CompetencyService competencyService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private RoleCompetencyRepository roleCompetencyRepository;

    @Autowired
    private MessageSource messageSource;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Assigning competencies to the role";

    @Override
    public List<RoleCompetencyDto> findAll() {
        return roleCompetencyRepository.findAll().stream().map(appMapper::toDto).toList();
    }

    @Override
    public List<RoleCompetencyDto> findAllByIdIn(Set<RoleCompetencyId> roleCompetencyIds) {
        return roleCompetencyRepository.findAllByIdIn(roleCompetencyIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleCompetencyDto> findAllByRoleId(Long roleId) {
        return roleCompetencyRepository.findByRoleId(roleId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleCompetencyDto> createAll(List<RoleCompetencyDto> roleCompetencyDtoList) {
        if (roleCompetencyDtoList == null || roleCompetencyDtoList.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> roleIds = roleCompetencyDtoList.stream()
                .map(dto -> dto.getId().getRoleId()).collect(Collectors.toSet());

        Set<Long> competencyIds = roleCompetencyDtoList.stream()
                .map(dto -> dto.getId().getCompetencyId()).collect(Collectors.toSet());

        Set<RoleCompetencyId> roleCompetencyIds = roleCompetencyDtoList.stream()
                .map(RoleCompetencyDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompetencyIds = competencyService.findAllByIsDeletedIsFalseAndIdIn(competencyIds)
                .stream().map(CompetencyDto::getId).collect(Collectors.toSet());

        Set<RoleCompetencyId> existingRoleCompetencyIds = this.findAllByIdIn(roleCompetencyIds).stream()
                .map(RoleCompetencyDto::getId).collect(Collectors.toSet());

        for (RoleCompetencyDto dto : roleCompetencyDtoList) {
            Long roleId = dto.getId().getRoleId();
            Long competencyId = dto.getId().getCompetencyId();

            if (!existingRoleIds.contains(roleId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingCompetencyIds.contains(competencyId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingRoleCompetencyIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE, null, Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }

        List<RoleCompetency> entities = roleCompetencyDtoList.stream()
                .map(appMapper::toEntity).collect(Collectors.toList());

        return roleCompetencyRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<RoleCompetencyDto> updateAll
            (Set<RoleCompetencyId> roleCompetencyIds, List<RoleCompetencyDto> roleCompetencyDtoList) {
        if (roleCompetencyDtoList == null || roleCompetencyDtoList.isEmpty() || roleCompetencyIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> roleIds = roleCompetencyIds.stream().map(RoleCompetencyId::getRoleId).collect(Collectors.toSet());

        Set<Long> competencyIds = roleCompetencyIds.stream().map(RoleCompetencyId::getCompetencyId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<Long> existingCompetencyIds = competencyService.findAllByIsDeletedIsFalseAndIdIn(competencyIds)
                .stream().map(CompetencyDto::getId).collect(Collectors.toSet());

        List<RoleCompetency> entities = roleCompetencyDtoList.stream().map(dto -> {
                    Long roleId = dto.getId().getRoleId();
                    Long competencyId = dto.getId().getCompetencyId();

                    if (!existingRoleIds.contains(roleId)) {
                        String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault());
                        throw new DataAccessException(errorMessage);
                    }

                    if (!existingCompetencyIds.contains(competencyId)) {
                        String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault());
                        throw new DataAccessException(errorMessage);
                    }

                    return appMapper.toEntity(dto);
                })
                .toList();

        return roleCompetencyRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<RoleCompetencyId> roleCompetencyIds) {
        roleCompetencyRepository.deleteAllByIdIn(roleCompetencyIds);
    }

    @Transactional
    @Override
    public void deleteAllByRoleId(Long roleId) {
        roleCompetencyRepository.deleteAllByRoleId(roleId);
    }

    @Transactional
    @Override
    public void deleteAllByRoleIdIn(Set<Long> roleIds) {
        roleCompetencyRepository.deleteAllByRoleIdIn(roleIds);
    }
}
