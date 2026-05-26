package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleAuthority;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import com.tbm.careerpathlearning.repository.RoleAuthorityRepository;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoleAuthorityServiceImpl implements RoleAuthorityService {

    @Autowired
    private RoleAuthorityRepository roleAuthorityRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String RECORD_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String RECORD_REDUNDANT_ERR_MSG_CODE = "database.record.redundant.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String GRANTING_ACCESS = "granting access";

    @Override
    public List<RoleAuthorityDto> getAll() {
        return roleAuthorityRepository.findAll().stream().map(this.appMapper::toDto).toList();
    }

    @Override
    public List<RoleAuthorityDto> getAllByIdIn(Set<RoleAuthorityId> roleAuthorityId) {
        return roleAuthorityRepository.findAllById(roleAuthorityId).stream().map(this.appMapper::toDto).toList();
    }

    @Override
    public Optional<RoleAuthorityDto> findById(RoleAuthorityId roleAuthorityId) {
        return roleAuthorityRepository.findById(roleAuthorityId)
                .map(appMapper::toDto);
    }

    @Override
    public List<RoleAuthorityDto> getAllByRoleId(Long roleId) {
        return this.roleAuthorityRepository.findByRoleId(roleId).stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleAuthorityDto> getAllByRoleIdIn(Set<Long> roleIds) {
        return this.roleAuthorityRepository.findAllByRoleIdIn(roleIds).stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleDto> getDistinctRole() {
        return this.roleAuthorityRepository.findDistinctRole().stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public Map<Long, Boolean> getRoleAuthorityMapByRoleId(Long roleId) {
        List<AuthorityDto> allAuthorities = authorityService.findAll();
        List<RoleAuthorityDto> roleAuthorityDtoList = this.getAllByRoleId(roleId);

        Set<Long> authorityIds = roleAuthorityDtoList.stream()
                .map(roleAuthorityDto -> roleAuthorityDto.getId().getAuthorityId())
                .collect(Collectors.toSet());

        return allAuthorities.stream()
                .collect(Collectors.toMap(
                        AuthorityDto::getId,
                        authority -> authorityIds.contains(authority.getId())
                ));
    }

    @Transactional
    @Override
    public RoleAuthorityDto create(RoleAuthorityDto roleAuthorityDto) {
        if (roleAuthorityDto == null) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{GRANTING_ACCESS}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (roleAuthorityDto.getRole() == null) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{GRANTING_ACCESS}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (roleAuthorityDto.getAuthority() == null) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{GRANTING_ACCESS}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Long roleId = roleAuthorityDto.getId().getRoleId();
        Long authorityId = roleAuthorityDto.getId().getAuthorityId();

        roleService.getAllById(roleId); // check if role existed
        authorityService.findById(authorityId);  // check if authority existed

        Optional<RoleAuthorityDto> existedRoleAuthority = this.findById(new RoleAuthorityId(roleId, authorityId));

        if (existedRoleAuthority.isPresent()) {
            String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                    null,
                    Locale.getDefault());

            throw new DataAccessException(errorTitle, errorMessage);
        }

        RoleAuthority roleAuthority = this.appMapper.toEntity(roleAuthorityDto);
        return this.appMapper.toDto(this.roleAuthorityRepository.save(roleAuthority));
    }

    @Override
    @Transactional
    public List<RoleAuthorityDto> createAll(List<RoleAuthorityDto> roleAuthorityDtoList) {
        if (roleAuthorityDtoList == null || roleAuthorityDtoList.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{GRANTING_ACCESS}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> roleIds = roleAuthorityDtoList.stream()
                .map(dto -> dto.getId().getRoleId())
                .collect(Collectors.toSet());

        Set<Long> authorityIds = roleAuthorityDtoList.stream()
                .map(dto -> dto.getId().getAuthorityId())
                .collect(Collectors.toSet());

        Set<RoleAuthorityId> roleAuthorityIds = roleAuthorityDtoList.stream()
                .map(RoleAuthorityDto::getId)
                .collect(Collectors.toSet());

        Set<Long> existingRoleIds = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .map(RoleDto::getId)
                .collect(Collectors.toSet());

        Set<Long> existingAuthorityIds = authorityService.findAllByIdIn(authorityIds).stream()
                .map(AuthorityDto::getId)
                .collect(Collectors.toSet());

        Map<RoleAuthorityId, RoleAuthorityDto> existingRoleAuthoritiesMap = this.getAllByIdIn(roleAuthorityIds)
                .stream().collect(Collectors.toMap(
                        RoleAuthorityDto::getId,
                        Function.identity()
                ));

        Set<RoleAuthorityId> existingRoleAuthorityIds = existingRoleAuthoritiesMap.keySet();

        for (RoleAuthorityDto dto : roleAuthorityDtoList) {
            Long roleId = dto.getId().getRoleId();
            Long authorityId = dto.getId().getAuthorityId();

            if (!existingRoleIds.contains(roleId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (!existingAuthorityIds.contains(authorityId)) {
                String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                throw new DataAccessException(errorMessage);
            }

            if (existingRoleAuthorityIds.contains(dto.getId())) {
                roleAuthorityDtoList.stream().filter(roleAuthorityDto -> roleAuthorityDto.getId().equals(dto.getId()))
                        .findFirst()
                        .ifPresent(roleAuthorityDto -> {
                            roleAuthorityDto.setCreatedBy(existingRoleAuthoritiesMap.get(dto.getId()).getCreatedBy());
                            roleAuthorityDto.setCreatedAt(existingRoleAuthoritiesMap.get(dto.getId()).getCreatedAt());
                        });
                //avoid redundancy
            }
        }

        List<RoleAuthority> entities = roleAuthorityDtoList.stream()
                .map(appMapper::toEntity)
                .toList();

        return roleAuthorityRepository.saveAll(entities).stream()
                .map(appMapper::toDto)
                .toList();
    }

    @Transactional
    @Override
    public void deleteById(RoleAuthorityId roleAuthorityId) {
        roleAuthorityRepository.deleteById(roleAuthorityId);
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<RoleAuthorityId> roleAuthorityIds) {
        roleAuthorityRepository.deleteAllById(roleAuthorityIds);
    }

    @Transactional
    @Override
    public void deleteAllByRoleIdIn(Set<Long> roleIds) {
        roleAuthorityRepository.deleteAllByRole_IdIn(roleIds);
    }

    @Transactional
    @Override
    public void deleteAllByRoleId(Long roleId) {
        roleAuthorityRepository.deleteAllByRole_Id(roleId);
    }

    @Transactional
    @Override
    public void deleteByRoleIdAndAuthorityIdIn(Long roleId, Set<Long> authorityIds) {
        roleAuthorityRepository.deleteAllByAuthority_IdInAndRole_Id(authorityIds, roleId);
    }

}
