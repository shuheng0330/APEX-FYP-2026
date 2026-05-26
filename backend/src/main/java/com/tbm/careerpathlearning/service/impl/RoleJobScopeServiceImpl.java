package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleJobScope;
import com.tbm.careerpathlearning.model.RoleJobScopeId;
import com.tbm.careerpathlearning.repository.RoleJobScopeRepository;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleJobScopeServiceImpl implements RoleJobScopeService {

    @Autowired
    private RoleService roleService;

    @Autowired
    private JobScopeService jobScopeService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private RoleJobScopeRepository roleJobScopeRepository;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Assigning job scopes to the role";

    @Override
    public List<RoleJobScopeDto> findAll() {
        return roleJobScopeRepository.findAll().stream().map(this.appMapper::toDto).toList();
    }

    @Override
    public List<RoleJobScopeDto> findAllByIdIn(Set<RoleJobScopeId> roleJobScopeIds) {
        return roleJobScopeRepository.findAllById(roleJobScopeIds).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeDto> findAllByRoleId(Long roleId) {
        return roleJobScopeRepository.findAllByRole_Id(roleId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeDto> findAllByRoleIdIn(Set<Long> roleIds) {
        return roleJobScopeRepository.findAllByRole_IdIn(roleIds).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeDto> findAllByJobScopeIdIn(Set<Long> jobScopeId) {
        return roleJobScopeRepository.findAllByJobScope_IdIn(jobScopeId).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeDto> findJobScopesUsedByOtherRoles(Set<Long> jobScopeIds, Long roleId) {
        return roleJobScopeRepository.findAllByJobScope_IdInAndRole_IdNot(jobScopeIds, roleId).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleJobScopeDto> findJobScopesUsedByRoleIdNotIn(Set<Long> jobScopeIds, Set<Long> roleIds) {
        return roleJobScopeRepository.findAllByJobScope_IdInAndRole_IdNotIn(jobScopeIds, roleIds).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleJobScopeDto> createAll(List<RoleJobScopeDto> roleJobScopeDtoList) {
        if (roleJobScopeDtoList == null || roleJobScopeDtoList.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> roleIds = roleJobScopeDtoList.stream()
                .map(dto -> dto.getId().getRoleId()).collect(Collectors.toSet());

        Set<Long> jobScopeIds = roleJobScopeDtoList.stream()
                .map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

        Set<RoleJobScopeId> roleJobScopeIds = roleJobScopeDtoList.stream()
                .map(RoleJobScopeDto::getId).collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId).collect(Collectors.toSet());

        Set<Long> existingJobScopeIds = jobScopeService.findAllByIsDeletedIsFalseAndIdIn(jobScopeIds)
                .stream().map(JobScopeDto::getId).collect(Collectors.toSet());

        Set<RoleJobScopeId> existingRoleJobScopeIds = this.findAllByIdIn(roleJobScopeIds).stream()
                .map(RoleJobScopeDto::getId).collect(Collectors.toSet());

        for (RoleJobScopeDto dto : roleJobScopeDtoList) {
            Long roleId = dto.getId().getRoleId();
            Long jobScopeId = dto.getId().getJobScopeId();

            if (!existingRoleIds.contains(roleId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingJobScopeIds.contains(jobScopeId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingRoleJobScopeIds.contains(dto.getId())) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null,
                        Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }

        List<RoleJobScope> entities = roleJobScopeDtoList.stream()
                .map(this.appMapper::toEntity).collect(Collectors.toList());

        return roleJobScopeRepository.saveAll(entities).stream()
                .map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteByRoleIdAndJobScopeIdIn(Long roleId, Set<Long> jobScopeIds) {
        roleJobScopeRepository.deleteAllByRole_IdAndJobScope_IdIn(roleId, jobScopeIds);
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<RoleJobScopeId> roleJobScopeIds) {
        roleJobScopeRepository.deleteAllById(roleJobScopeIds);
    }

    @Transactional
    @Override
    public List<RoleJobScopeDto> findAndDeleteAllByRoleId(Long roleId) {
        List<RoleJobScope> toDelete = roleJobScopeRepository.findAllByRole_Id(roleId);
        roleJobScopeRepository.deleteAll(toDelete);

        return toDelete.stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleJobScopeDto> findAndDeleteAllByRoleIdIn(Set<Long> roleIds) {
        List<RoleJobScope> toDelete = roleJobScopeRepository.findAllByRole_IdIn(roleIds);
        roleJobScopeRepository.deleteAll(toDelete);

        return toDelete.stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }
}
