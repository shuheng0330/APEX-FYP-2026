package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.repository.RoleRepository;
import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleRepository roleRepository;

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

    private static final String UPDATE_OPERATION = "Update Role";

    private static final String CREATE_OPERATION = "Create Role";

    private static final String ROLE_NAME_ATTRIBUTE = "role name";

    @Override
    public List<RoleDto> getAll() {
        return roleRepository.findAll().stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<RoleDto> getAllByDeletedIsFalse() {
        return roleRepository.findAllByIsDeletedIsFalse().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public RoleDto getAllById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new DataAccessException(
                        messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, new String[]{}, Locale.getDefault())
                ));
        return appMapper.toDto(role);
    }

    @Override
    public List<RoleDto> getAllByIsDeletedIsFalseAndIdIn(Set<Long> ids) {
        List<Role> roles = roleRepository.findAllByIdInAndIsDeletedIsFalse(ids);

        return roles.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public RoleDto create(RoleDto dto) {
        if (dto.getOrgChart() == null || validationService.isNullOrBlank(dto.getName())
                || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        this.checkRedundancyWithinSameDepartmentByName(dto.getOrgChart().getId(), null, dto.getName());

        return appMapper.toDto(this.roleRepository.save(appMapper.toEntity(dto)));
    }

    @Transactional
    @Override
    public List<RoleDto> createAll(List<RoleDto> dtos) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Map<Long, Set<String>> nameSeenWithinDepartment = new HashMap<>();
        Map<Long, Set<String>> nameDuplicates = new HashMap<>();

        List<RoleDto> existingRoleDto = this.getAllByDeletedIsFalse();

        List<Role> entities = dtos.stream().map(dto -> {
            if (validationService.isNullOrBlank(dto.getName())
                    || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                        new String[]{CREATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!nameSeenWithinDepartment
                    .computeIfAbsent(dto.getOrgChart().getId(), k -> new HashSet<>())
                    .add(dto.getName().toLowerCase().trim())) {
                nameDuplicates
                        .computeIfAbsent(dto.getOrgChart().getId(), k -> new HashSet<>())
                        .add(dto.getName().toLowerCase().trim());
            }

            if (!nameDuplicates.isEmpty()) {
                String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{ROLE_NAME_ATTRIBUTE}, Locale.getDefault());
                String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                        new String[]{ROLE_NAME_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorTitle, errorMessage);
            }

            Set<Long> duplicatedOrgChartId = existingRoleDto.stream().filter(role ->
                            role.getName().equalsIgnoreCase(dto.getName().trim().toLowerCase()))
                    .map(role -> role.getOrgChart().getId()).collect(Collectors.toSet());

            if (duplicatedOrgChartId.contains(dto.getOrgChart().getId())) {
                String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{ROLE_NAME_ATTRIBUTE}, Locale.getDefault());
                String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                        new String[]{ROLE_NAME_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorTitle, errorMessage);
            }

            return appMapper.toEntity(dto);
        }).toList();

        return roleRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    private void checkRedundancyWithinSameDepartmentByName(Long orgChartId, Long roleId, String roleName) {
        Optional<RoleDto> existedRoleDto = roleRepository.findAllByIsDeletedIsFalseAndOrgChart_IdAndNameIgnoreCase(orgChartId, roleName)
                .map(appMapper::toDto);

        if (roleId == null) {
            if (existedRoleDto.isPresent()) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null, Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        } else {
            if (existedRoleDto.isPresent() && !existedRoleDto.get().getId().equals(roleId)) {
                String errorTitle = messageSource.getMessage(RECORD_REDUNDANT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(RECORD_REDUNDANT_ERR_MSG_CODE,
                        null, Locale.getDefault());

                throw new DataAccessException(errorTitle, errorMessage);
            }
        }

    }

    private void checkNameRedundancyForBulkUpdate(List<RoleDto> roleDtos) {
        Set<Long> roleIds = roleDtos.stream().map(RoleDto::getId).collect(Collectors.toSet());
        List<RoleDto> existedRoleDto = roleRepository.findAllById(roleIds).stream().map(appMapper::toDto).toList();
        Set<String> existedRoleName = existedRoleDto.stream().map(RoleDto::getName).collect(Collectors.toSet());
        List<String> incomingRoleName = roleDtos.stream().map(RoleDto::getName).toList();

        Set<String> seen = new HashSet<>();
        Set<String> incomingNameDuplicated = incomingRoleName.stream()
                .filter(name -> !seen.add(name))
                .collect(Collectors.toSet());

        if (!incomingNameDuplicated.isEmpty()) {
            String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{ROLE_NAME_ATTRIBUTE}, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                    new String[]{ROLE_NAME_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        Set<String> existedNames = new HashSet<>(existedRoleName);
        Set<String> roleNameDuplicated = incomingRoleName.stream()
                .filter(name -> !existedNames.add(name))
                .collect(Collectors.toSet());

        if (!roleNameDuplicated.isEmpty()) {
            Map<String, RoleDto> existedRoleNameMap = existedRoleDto.stream().collect(Collectors.toMap(
                    RoleDto::getName,
                    Function.identity()
            ));

            Map<String, RoleDto> incomingRoleNameMap = roleDtos.stream().collect(Collectors.toMap(
                    RoleDto::getName,
                    Function.identity()
            ));

            for (String roleName : roleNameDuplicated) {
                RoleDto existedRecord = existedRoleNameMap.get(roleName);
                RoleDto incomingDto = incomingRoleNameMap.get(roleName);
                if (!Objects.equals(existedRecord.getId(), incomingDto.getId())) { // check if the same record
                    // not the same record
                    if (Objects.equals(existedRecord.getOrgChart().getId(), incomingDto.getOrgChart().getId())) { // check if coming from same deparment
                        // from the same department
                        String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{ROLE_NAME_ATTRIBUTE}, Locale.getDefault());
                        String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                                new String[]{ROLE_NAME_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault());

                        throw new BadRequestException(errorTitle, errorMessage);
                    }
                }
            }
        }
    }

    @Transactional
    @Override
    public RoleDto update(Long id, RoleDto dto) {

        RoleDto roleDtoToBeUpdated = this.getAllById(id);

        if (dto.getOrgChart() == null) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (validationService.isNullOrBlank(dto.getName())
                || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        this.checkRedundancyWithinSameDepartmentByName(dto.getOrgChart().getId(), id, dto.getName());

        roleDtoToBeUpdated.setName(dto.getName());
        roleDtoToBeUpdated.setOrgChart(dto.getOrgChart());
        roleDtoToBeUpdated.setDescription(dto.getDescription());
        roleDtoToBeUpdated.setVisible(dto.isVisible());
        roleDtoToBeUpdated.setDeleted(dto.isDeleted());
        roleDtoToBeUpdated.setUpdatedBy(dto.getUpdatedBy());
        roleDtoToBeUpdated.setUpdatedAt(dto.getUpdatedAt());

        return appMapper.toDto(roleRepository.save(appMapper.toEntity(roleDtoToBeUpdated)));
    }

    @Transactional
    @Override
    public List<RoleDto> updateAll(Set<Long> ids, List<RoleDto> dtos) {

        this.checkNameRedundancyForBulkUpdate(dtos);

        List<RoleDto> roleDtoToBeUpdated = this.getAllByIsDeletedIsFalseAndIdIn(ids);

        Map<Long, RoleDto> dtoMap = dtos.stream()
                .collect(Collectors.toMap(
                        RoleDto::getId,
                        Function.identity()));

        List<Role> entitIes = roleDtoToBeUpdated.stream().map(dto -> {
            if (dtoMap.get(dto.getId()).getOrgChart() == null) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                        new String[]{UPDATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (validationService.isNullOrBlank(dtoMap.get(dto.getId()).getName())
                    || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                        new String[]{UPDATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            dto.setName(dtoMap.get(dto.getId()).getName());
            dto.setOrgChart(dtoMap.get(dto.getId()).getOrgChart());
            dto.setDescription(dtoMap.get(dto.getId()).getDescription());
            dto.setVisible(dtoMap.get(dto.getId()).isVisible());
            dto.setDeleted(dtoMap.get(dto.getId()).isDeleted());
            dto.setUpdatedBy(dtoMap.get(dto.getId()).getUpdatedBy());
            dto.setUpdatedAt(dtoMap.get(dto.getId()).getUpdatedAt());

            return appMapper.toEntity(dto);
        }).toList();

        return roleRepository.saveAll(entitIes).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<RoleDto> createAndUpdateAll(List<RoleDto> dtos) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Map<Long, Set<String>> nameSeenWithinDepartment = new HashMap<>();
        Map<Long, Set<String>> nameDuplicates = new HashMap<>();

        List<RoleDto> existingRoleDto = this.getAllByDeletedIsFalse();
        Map<Long, RoleDto> existingRoleIdMap = existingRoleDto.stream().collect(Collectors.toMap(
                RoleDto::getId,
                Function.identity()
        ));
        Set<String> existingRoleName = existingRoleDto.stream().map(role -> role.getName().toLowerCase().trim())
                .collect(Collectors.toSet());

        List<Role> entities = dtos.stream().map(dto -> {
            if (dto.getOrgChart() == null || validationService.isNullOrBlank(dto.getName())
                    || dto.getName().trim().length() > 255 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
                String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                        new String[]{UPDATE_OPERATION}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            RoleDto roleDto;
            if (dto.getId() != null) {
                roleDto = existingRoleIdMap.get(dto.getId());
            } else {
                roleDto = dto;
                roleDto.setCreatedAt(dto.getCreatedAt());
                roleDto.setCreatedBy(dto.getCreatedBy());
            }

            roleDto.setOrgChart(dto.getOrgChart());

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
                    String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{ROLE_NAME_ATTRIBUTE}, Locale.getDefault());
                    String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                            new String[]{ROLE_NAME_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault());

                    throw new BadRequestException(errorTitle, errorMessage);
                }

                if (existingRoleName.contains(dto.getName().toLowerCase().trim())) {
                    RoleDto existingRole = existingRoleDto.stream().filter(role ->
                                    role.getName().equalsIgnoreCase(dto.getName().trim())
                                            && Objects.equals(role.getOrgChart().getId(), dto.getOrgChart().getId())
                                            && !Objects.equals(role.getId(), dto.getId()))
                            .findAny().orElse(null);
                    if (existingRole != null) { // from the same department
                        String errorTitle = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_TITLE_CODE, new String[]{ROLE_NAME_ATTRIBUTE}, Locale.getDefault());
                        String errorMessage = messageSource.getMessage(ATTRIBUTE_UNIQUE_ERR_MSG_CODE,
                                new String[]{ROLE_NAME_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault());

                        throw new BadRequestException(errorTitle, errorMessage);
                    }
                }

                if (dto.getId() != null) {
                    String existingName = existingRoleIdMap.get(dto.getId()).getName().toLowerCase().trim();
                    if (!dto.getName().equalsIgnoreCase(existingName)) {
                        existingRoleName.remove(existingName);
                        existingRoleName.add(dto.getName().toLowerCase().trim());
                    }
                }
            } else {
                if (dto.getId() != null) {
                    String existingName = existingRoleIdMap.get(dto.getId()).getName().toLowerCase().trim();
                    existingRoleName.remove(existingName);
                }
            }

            roleDto.setName(dto.getName());
            roleDto.setDescription(dto.getDescription() == null || dto.getDescription().trim().isEmpty() ? null : dto.getDescription().trim());
            roleDto.setVisible(dto.isVisible());
            roleDto.setDeleted(dto.isDeleted());

            roleDto.setUpdatedBy(dto.getUpdatedBy());
            roleDto.setUpdatedAt(dto.getUpdatedAt());

            return appMapper.toEntity(roleDto);
        }).toList();

        return roleRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void delete(Long roleId, UUID userId) {
        RoleDto roleDto = this.getAllById(roleId);

        roleDto.setDeleted(true);
        roleDto.setUpdatedBy(userId);
        roleDto.setUpdatedAt(OffsetDateTime.now());

        roleRepository.save(appMapper.toEntity(roleDto));
    }

    @Transactional
    @Override
    public void deleteAllByRoleIdIn(Set<Long> roleIds, UUID userId) {
        List<RoleDto> roleDtoList = this.getAllByIsDeletedIsFalseAndIdIn(roleIds);

        roleDtoList.forEach(roleDto -> {
            roleDto.setDeleted(true);
            roleDto.setUpdatedAt(OffsetDateTime.now());
            roleDto.setUpdatedBy(userId);
        });

        roleRepository.saveAll(roleDtoList.stream().map(appMapper::toEntity).collect(Collectors.toList()));
    }

    @Transactional
    @Override
    public List<RoleDto> findAndDeleteAllByOrgChartIdIn(Set<Long> orgChartIds, UUID userId, OffsetDateTime now) {
        List<Role> toDelete = roleRepository.findAllByOrgChartIdInAndIsDeletedIsFalse(orgChartIds).stream()
                .peek(role -> {
                    role.setDeleted(true);
                    role.setUpdatedAt(OffsetDateTime.now());
                    role.setUpdatedBy(userId);
                })
                .toList();

        roleRepository.saveAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }
}
