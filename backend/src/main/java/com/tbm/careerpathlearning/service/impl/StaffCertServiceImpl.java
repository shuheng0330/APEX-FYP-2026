package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffCertDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.StaffCert;
import com.tbm.careerpathlearning.repository.StaffCertRepository;
import com.tbm.careerpathlearning.service.StaffCertService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StaffCertServiceImpl implements StaffCertService {
    @Autowired
    private StaffCertRepository staffCertRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private AppMapper appMapper;

    private static final Logger logger = LoggerFactory.getLogger(StaffCertServiceImpl.class);

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String NAME_ATTRIBUTE = "Certificate Name";

    private static final String CREATE_OPERATION = "Upload Certificate";

    private static final String UPDATE_OPERATION = "Update Staff Profile";

    @Override
    public StaffCertDto findById(Long id) {
        return this.staffCertRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new BadRequestException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public Optional<StaffCertDto> findByStaff_IdAndFileNameIgnoreCase(UUID staffId, String fileName) {
        return staffCertRepository.findByStaff_IdAndFileNameIgnoreCase(staffId, fileName)
                .map(appMapper::toDto);
    }

    @Override
    public List<StaffCertDto> findByStaffId(UUID staffId) {
        return staffCertRepository.findAllByStaff_Id(staffId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public StaffCertDto create(StaffCertDto dto) {
        if (dto.getStaff() == null || validationService.isNullOrBlank(dto.getFileName()) || validationService.isNullOrBlank(dto.getCertPath())
                || dto.getCertName().trim().length() > 255 || (dto.getFileName() != null && dto.getFileName().trim().length() > 259)
                || dto.getCertPath().trim().length() > 1000 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault()));
        }

        Optional<StaffCertDto> exitedProfile = this.findByStaff_IdAndFileNameIgnoreCase(dto.getStaff().getId(), dto.getFileName().trim());

        if (exitedProfile.isPresent()) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE, new String[]{NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault()));
        }

        return appMapper.toDto(staffCertRepository.save(appMapper.toEntity(dto)));
    }

    @Transactional
    @Override
    public StaffCertDto update(StaffCertDto dto) {
        if (dto.getStaff() == null || validationService.isNullOrBlank(dto.getFileName()) || validationService.isNullOrBlank(dto.getCertPath())
                || dto.getCertName().trim().length() > 255 || (dto.getFileName() != null && dto.getFileName().trim().length() > 259)
                || dto.getCertPath().trim().length() > 1000 || (dto.getDescription() != null && dto.getDescription().trim().length() > 1000)) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        Optional<StaffCertDto> exitedProfile = this.findByStaff_IdAndFileNameIgnoreCase(dto.getStaff().getId(), dto.getFileName().trim());

        if (exitedProfile.isPresent() && !Objects.equals(exitedProfile.get().getId(), dto.getId())) {
            throw new BadRequestException(messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_TITLE_CODE, new String[]{NAME_ATTRIBUTE}, Locale.getDefault()),
                    messageSource.getMessage(UNIQUE_ATTRIBUTE_REQUIRED_ERR_MSG_CODE, new String[]{NAME_ATTRIBUTE, CREATE_OPERATION}, Locale.getDefault()));
        }

        return appMapper.toDto(staffCertRepository.save(appMapper.toEntity(dto)));
    }

    @Transactional
    @Override
    public StaffCertDto findAndDeleteById(Long id) {
        StaffCert toDelete = staffCertRepository.findById(id).orElse(null);
        staffCertRepository.deleteById(id);

        return appMapper.toDto(toDelete);
    }

    @Transactional
    @Override
    public List<StaffCertDto> findAndDeleteByIdIn(Set<Long> ids) {
        List<StaffCert> toDelete = staffCertRepository.findAllById(ids);
        staffCertRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllByStaffId(UUID staffId) {
        staffCertRepository.deleteAllByStaff_Id(staffId);
    }

    @Transactional
    @Override
    public void deleteAllByStaffIdIn(Set<UUID> ids) {
        staffCertRepository.deleteAllByStaff_IdIn(ids);
    }
}
