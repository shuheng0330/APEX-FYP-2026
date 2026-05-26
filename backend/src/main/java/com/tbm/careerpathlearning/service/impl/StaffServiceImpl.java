package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CareerPathwayDto;
import com.tbm.careerpathlearning.dto.ParentChildNodeDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.ParentChildNodeService;
import com.tbm.careerpathlearning.service.StaffService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StaffServiceImpl implements StaffService {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private ParentChildNodeService parentChildNodeService;

    @Autowired
    private AppMapper appMapper;

    private static final Logger logger = LoggerFactory.getLogger(StaffServiceImpl.class);

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String UNIQUE_ATTRIBUTE_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String UNIQUE_ATTRIBUTE_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UPDATE_OPERATION = "Update Staff Account";

    private static final String CREATE_OPERATION = "Create Staff Account";

    private static final String EMAIL_ATTRIBUTE = "Email";

    @Override
    public List<StaffDto> findAll() {
        return staffRepository.findAll().stream()
                .map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffDto> findAllByIsDeletedIsFalse() {
        return staffRepository.findAllByIsDeletedIsFalse().stream()
                .map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public StaffDto findById(UUID id) {
        StaffDto result = this.staffRepository.findById(id).map(this.appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
        result.setPassword(null);

        return result;
    }

    @Override
    public List<StaffDto> findAllByIdIn(Set<UUID> ids) {
        return this.staffRepository.findAllById(ids).stream().map(this.appMapper::toDto).peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<StaffDto> findByIsDeletedIsFalseAndEmail(String email) { // used for credential verification
        return this.staffRepository.findByIsDeletedIsFalseAndEmail(email).map(this.appMapper::toDto);
    }

    @Override
    public List<StaffDto> findAllByIsDeletedIsFalseAndManagerId(UUID managerId) {
        return this.staffRepository.findAllByIsDeletedIsFalseAndManager_Id(managerId).stream()
                .map(this.appMapper::toDto).peek(dto -> dto.setPassword(null)).toList();
    }

    @Override
    public List<StaffDto> findAllByIsDeletedIsFalseAndIdIn(Set<UUID> ids) {
        return staffRepository.findAllByIsDeletedIsFalseAndIdIn(ids).stream().map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffDto> findAllByRoleId(Long roleId) {
        return this.staffRepository.findByRoleId(roleId).stream()
                .map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffDto> findAllByRoleIdIn(Set<Long> roleIds) {
        return this.staffRepository.findAllByRoleIdIn(roleIds).stream().map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffDto> findAllByCareerPathwayId(Long careerPathwayId) {
        return this.staffRepository.findAllByCareerPathway_Id(careerPathwayId).stream()
                .map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffDto> findAllByCareerPathwayIdIn(Set<Long> careerPathwayIds) {
        return this.staffRepository.findAllByCareerPathway_IdIn(careerPathwayIds).stream().map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StaffDto update(UUID staffId, StaffDto dto) {
        if (staffId == null || validationService.isNullOrBlank(dto.getEmail())
                || dto.getEmail().trim().length() > 255 || (dto.getName() != null && dto.getName().trim().length() > 255)
                || (dto.getPassword() != null && dto.getPassword().trim().length() > 255)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        StaffDto staffDto = this.staffRepository.findById(staffId).map(this.appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));

        Optional<StaffDto> existedStaff = this.findByIsDeletedIsFalseAndEmail(dto.getEmail());

        if (existedStaff.isPresent() && !existedStaff.get().getId().equals(staffId)) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_TITLE_CODE, new String[]{EMAIL_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_MSG_CODE, new String[]{EMAIL_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault()));
        }

        staffDto.setName(dto.getName());
        staffDto.setEmail(dto.getEmail().toLowerCase().trim());
        staffDto.setRole(dto.getRole());
        staffDto.setCareerPathway(dto.getCareerPathway());
        staffDto.setManager(dto.getManager());
        staffDto.setFirstLogin(dto.isFirstLogin());
        staffDto.setAccountStatus(dto.getAccountStatus());
        staffDto.setPassword(dto.getPassword() == null ? staffDto.getPassword() : dto.getPassword());
        // only update the password if it is not null

        staffDto.setDeleted(dto.isDeleted());
        staffDto.setUpdatedAt(dto.getUpdatedAt());
        staffDto.setUpdatedBy(dto.getUpdatedBy());

        StaffDto updatedStaff = appMapper.toDto(staffRepository.save(appMapper.toEntity(staffDto)));
        updatedStaff.setPassword(null);

        return updatedStaff;
    }

    @Transactional
    @Override
    public List<StaffDto> updateAll(List<StaffDto> dtos) {
        if (dtos.isEmpty()) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        Set<String> emailSeen = new HashSet<>();
        Set<String> emailDuplicates = dtos.stream()
                .map(StaffDto::getEmail)
                .filter(Objects::nonNull)
                .filter(email -> !emailSeen.add(email))
                .collect(Collectors.toSet());

        if (!emailDuplicates.isEmpty()) { // incoming emails duplicated
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_TITLE_CODE, new String[]{EMAIL_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_MSG_CODE, new String[]{EMAIL_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault()));
        }

        Map<UUID, StaffDto> existedStaffs = staffRepository.findAllByIsDeletedIsFalse().stream()
                .map(this.appMapper::toDto)
                .collect(Collectors.toMap(
                        StaffDto::getId,
                        Function.identity()
                ));

        List<Staff> entities = dtos.stream().map(dto -> {
                    Optional<StaffDto> existedEmail = existedStaffs.values().stream()
                            .filter(staff -> staff.getEmail().equalsIgnoreCase(dto.getEmail().toLowerCase().trim())
                                    && !Objects.equals(staff.getId(), dto.getId()))
                            .findFirst();

                    if (existedEmail.isPresent()) {
                        throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_TITLE_CODE, new String[]{EMAIL_ATTRIBUTE}, Locale.getDefault()),
                                messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_MSG_CODE, new String[]{EMAIL_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault()));
                    }

                    if (dto.getEmail().trim().length() > 255 || (dto.getName() != null && dto.getName().trim().length() > 255)
                            || (dto.getPassword() != null && dto.getPassword().trim().length() > 255)) {
                        throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
                    }

                    StaffDto staffDto = existedStaffs.get(dto.getId());

                    if (staffDto == null) {
                        staffDto = dto;
                        dto.setCreatedAt(dto.getCreatedAt());
                        dto.setUpdatedAt(dto.getUpdatedAt());
                    }

                    staffDto.setName(dto.getName());
                    staffDto.setEmail(dto.getEmail().toLowerCase().trim());
                    staffDto.setRole(dto.getRole());
                    staffDto.setCareerPathway(dto.getCareerPathway());
                    staffDto.setManager(dto.getManager());
                    staffDto.setFirstLogin(dto.isFirstLogin());
                    staffDto.setAccountStatus(dto.getAccountStatus());
                    staffDto.setPassword(dto.getPassword() == null ? staffDto.getPassword() : dto.getPassword());
                    // only update the password if it is not null

                    staffDto.setDeleted(dto.isDeleted());
                    staffDto.setUpdatedAt(dto.getUpdatedAt());
                    staffDto.setUpdatedBy(dto.getUpdatedBy());

                    return appMapper.toEntity(staffDto);
                })
                .toList();

        return staffRepository.saveAll(entities).stream().map(appMapper::toDto)
                .peek(dto -> dto.setPassword(null)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<StaffDto> updateRoleByStaffIdIn(Set<UUID> staffIds, RoleDto roleDto, UUID userId) {
        List<StaffDto> staffs = staffRepository.findAllById(staffIds).stream().map(this.appMapper::toDto).toList();

        staffs.forEach(staff -> {
            staff.setRole(roleDto);
            staff.setUpdatedAt(OffsetDateTime.now());
            staff.setUpdatedBy(userId);
        });

        List<Staff> entities = staffs.stream().map(this.appMapper::toEntity).collect(Collectors.toList());

        return this.staffRepository.saveAll(entities)
                .stream().map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<StaffDto> updateCareerPathwayByIdIn(Set<UUID> staffIds, CareerPathwayDto careerPathwayDto, UUID userId, OffsetDateTime now) {
        List<StaffDto> staffs = staffRepository.findAllById(staffIds).stream().map(this.appMapper::toDto).toList();

        List<Staff> entities = staffs.stream().map(dto -> {
                    dto.setCareerPathway(careerPathwayDto);
                    dto.setUpdatedAt(OffsetDateTime.now());
                    dto.setUpdatedBy(userId);

                    return appMapper.toEntity(dto);
                })
                .collect(Collectors.toList());

        return this.staffRepository.saveAll(entities)
                .stream().map(this.appMapper::toDto)
                .peek(dto -> dto.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StaffDto create(StaffDto dto) {
        if (validationService.isNullOrBlank(dto.getEmail())
                || dto.getEmail().trim().length() > 255 || (dto.getName() != null && dto.getName().trim().length() > 255)
                || (dto.getPassword() != null && dto.getPassword().trim().length() > 255)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault()));
        }

        Optional<StaffDto> exitedStaff = this.findByIsDeletedIsFalseAndEmail(dto.getEmail());

        if (exitedStaff.isPresent()) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_TITLE_CODE, new String[]{EMAIL_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_MSG_CODE, new String[]{EMAIL_ATTRIBUTE, UPDATE_OPERATION}, Locale.getDefault()));
        }

        StaffDto createdStaff = appMapper.toDto(this.staffRepository.save(this.appMapper.toEntity(dto)));
        createdStaff.setPassword(null);

        return createdStaff;
    }

    @Override
    @Transactional
    public void delete(UUID staffID, UUID userId) {
        StaffDto staffDto = this.staffRepository.findById(staffID).map(this.appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));

        staffDto.setDeleted(true);
        staffDto.setUpdatedBy(userId);
        staffDto.setUpdatedAt(OffsetDateTime.now());

        staffRepository.save(appMapper.toEntity(staffDto));
    }

    @Override
    @Transactional
    public void deleteAllById(Set<UUID> staffIds, UUID userId) {
        List<StaffDto> staffDtoList = staffRepository.findAllById(staffIds).stream().map(this.appMapper::toDto).toList();

        staffDtoList.forEach(staffDto -> {
            staffDto.setDeleted(true);
            staffDto.setUpdatedBy(userId);
            staffDto.setUpdatedAt(OffsetDateTime.now());
        });

        staffRepository.saveAll(staffDtoList.stream().map(appMapper::toEntity).collect(Collectors.toList()));
    }

    @Override
    public Set<UUID> getAllDownlineStaffIds(UUID userUUID) {

        List<StaffDto> allStaffs = staffRepository.findAllByIsDeletedIsFalse().stream()
                .map(appMapper::toDto)
                .toList();

        StaffDto user = allStaffs.stream()
                .filter(dto -> dto.getId().equals(userUUID))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Staff not found"));

        Long userOrgChartId = user.getRole().getOrgChart().getId();

        List<ParentChildNodeDto> relations =
                parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART);

        Set<Long> allDescendantOrgChartIds =
                findAllDescendants(userOrgChartId, relations);

        Set<UUID> fromChildrenDept = allStaffs.stream()
                .filter(dto -> dto.getRole() != null)
                .filter(dto -> allDescendantOrgChartIds
                        .contains(dto.getRole().getOrgChart().getId()))
                .map(StaffDto::getId)
                .collect(Collectors.toSet());

        Map<UUID, List<StaffDto>> staffByManagerMap = allStaffs.stream()
                .filter(s -> s.getManager() != null)
                .collect(Collectors.groupingBy(s -> s.getManager().getId()));

        Set<UUID> sameDeptSubordinates =
                getAllSubordinates(userUUID, staffByManagerMap)
                        .stream()
                        .map(StaffDto::getId)
                        .collect(Collectors.toSet());

        Set<UUID> result = new HashSet<>();
        result.addAll(fromChildrenDept);
        result.addAll(sameDeptSubordinates);
        result.add(userUUID);

        return result;
    }

    public Set<StaffDto> getAllSubordinates(UUID targetManagerId, Map<UUID, List<StaffDto>> staffByManagerMap) {
        Set<StaffDto> subordinates = new HashSet<>();

        List<StaffDto> directReports = staffByManagerMap.get(targetManagerId);

        if (directReports != null) {
            for (StaffDto report : directReports) {
                subordinates.add(report);

                // find the reports of this report
                subordinates.addAll(getAllSubordinates(report.getId(), staffByManagerMap));
            }
        }

        return subordinates;
    }

    private Set<Long> findAllDescendants(Long parentId, List<ParentChildNodeDto> relations) {
        Set<Long> descendants = new HashSet<>();

        List<Long> directChildren = relations.stream()
                .filter(r -> Objects.equals(r.getParentId(), parentId))
                .map(ParentChildNodeDto::getChildId)
                .toList();

        for (Long childId : directChildren) {
            descendants.add(childId);
            descendants.addAll(findAllDescendants(childId, relations));
        }

        return descendants;
    }
}
