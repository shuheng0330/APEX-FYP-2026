package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffProfileDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffProfile;
import com.tbm.careerpathlearning.repository.StaffProfileRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.StaffProfileService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StaffProfileServiceImpl implements StaffProfileService {

    @Autowired
    private StaffProfileRepository staffProfileRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private StaffRepository staffRepository;

    private static final Logger logger = LoggerFactory.getLogger(StaffProfileServiceImpl.class);

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String ID_ATTRIBUTE = "Staff ID";

    private static final String CREATE_OPERATION = "Create Staff Profile";

    private static final String UPDATE_OPERATION = "Update Staff Profile";


    @Override
    public List<StaffProfileDto> findAll() {
        return this.staffProfileRepository.findAll().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public StaffProfileDto findById(UUID id) {
        return this.staffProfileRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new BadRequestException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public Optional<StaffProfileDto> getById(UUID userId) {
        return this.staffProfileRepository.findById(userId).map(this.appMapper::toDto);
    }

    @Transactional
    @Override
    public StaffProfileDto update(UUID staffId, StaffProfileDto dto) {
        if (staffId == null || validationService.isNullOrBlank(dto.getContactNumber())
                || (dto.getAbout() != null && dto.getAbout().trim().length() > 1000)
                || (dto.getContactNumber() != null && dto.getContactNumber().trim().length() > 15)
                || (dto.getProfilePicturePath() != null && dto.getProfilePicturePath().trim().length() > 1000)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        StaffProfileDto staffDto = this.findById(staffId);

        staffDto.setContactNumber(dto.getContactNumber());
        staffDto.setAbout(dto.getAbout());
        staffDto.setProfilePicturePath(dto.getProfilePicturePath());
        staffDto.setUpdatedAt(dto.getUpdatedAt());
        staffDto.setUpdatedBy(dto.getUpdatedBy());

        return appMapper.toDto(staffProfileRepository.save(appMapper.toEntity(staffDto)));

    }

    @Transactional
    @Override
    public StaffProfileDto updateProfilePicture(UUID staffId, StaffProfileDto dto) {
        if (staffId == null || validationService.isNullOrBlank(dto.getProfilePicturePath()) ||
                dto.getProfilePicturePath().trim().length() > 1000) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        StaffProfileDto staffDto = this.findById(staffId);

        staffDto.setProfilePicturePath(dto.getProfilePicturePath());
        staffDto.setUpdatedAt(dto.getUpdatedAt());
        staffDto.setUpdatedBy(dto.getUpdatedBy());

        return appMapper.toDto(staffProfileRepository.save(appMapper.toEntity(staffDto)));

    }

    @Transactional
    @Override
    public StaffProfileDto create(StaffProfileDto dto) {
        if (dto.getStaffId() == null
                || (dto.getAbout() != null && dto.getAbout().trim().length() > 1000)
                || (dto.getContactNumber() != null && dto.getContactNumber().trim().length() > 15)
                || (dto.getProfilePicturePath() != null && dto.getProfilePicturePath().trim().length() > 1000)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault()));
        }

        Optional<StaffProfileDto> exitedProfile = this.getById(dto.getStaffId());

        if (exitedProfile.isPresent()) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE, new String[]{ID_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE, new String[]{ID_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault()));
        }

        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() ->
                        new BadRequestException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));

        StaffProfile entity = appMapper.toEntity(dto);
        entity.setStaffId(null);
        entity.setStaff(staff);

        return appMapper.toDto(staffProfileRepository.save(entity));
    }

    @Transactional
    @Override
    public List<StaffProfileDto> createAll(List<StaffProfileDto> dtos) {
        if (dtos.isEmpty()) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault()));
        }

        Set<UUID> existed = this.findAll().stream().map(StaffProfileDto::getStaffId).collect(Collectors.toSet());
        Map<UUID, Staff> existedStaff = staffRepository.findAllByIsDeletedIsFalse().stream().collect(Collectors.toMap(
                Staff::getId,
                Function.identity()
        ));

        List<StaffProfile> entities = dtos.stream().map(dto -> {
                    if (dto.getStaffId() == null || existed.contains(dto.getStaffId())
                            || (dto.getAbout() != null && dto.getAbout().trim().length() > 1000)
                            || (dto.getContactNumber() != null && dto.getContactNumber().trim().length() > 15)
                            || (dto.getProfilePicturePath() != null && dto.getProfilePicturePath().trim().length() > 1000)) {
                        throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault()));

                    }

                    StaffProfile entity = appMapper.toEntity(dto);
                    entity.setStaffId(null);
                    entity.setStaff(Objects.requireNonNull(existedStaff.get(dto.getStaffId())));

                    return entity;
                }
        ).toList();

        return staffProfileRepository.saveAll(entities).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void delete(UUID staffID) {
        staffProfileRepository.deleteById(staffID);
    }

    @Transactional
    @Override
    public void deleteAllById(Set<UUID> staffIds) {
        staffProfileRepository.deleteAllById(staffIds);
    }
}
