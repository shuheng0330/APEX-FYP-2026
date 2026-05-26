package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffLoginAuditDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.repository.StaffLoginAuditRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.StaffLoginAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StaffLoginAuditServiceImpl implements StaffLoginAuditService {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private StaffLoginAuditRepository staffLoginAuditRepository;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Create Staff Login Audit Record";

    private static final String UPDATE_OPERATION = "Create Staff Login Audit Record";

    @Override
    public List<StaffLoginAuditDto> getAllStaffLoginAudits() {
        return staffLoginAuditRepository.findAll().stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public StaffLoginAuditDto getStaffLoginAuditById(UUID id) {
        return staffLoginAuditRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public Optional<StaffLoginAuditDto> findStaffLoginAuditById(UUID id) {
        return staffLoginAuditRepository.findById(id).map(appMapper::toDto);
    }

    @Override
    public List<StaffLoginAuditDto> getStaffLoginAuditsByIdIn(Set<UUID> ids) {
        return staffLoginAuditRepository.findAllById(ids).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StaffLoginAuditDto updateStaffLoginAudit(UUID staffId, StaffLoginAuditDto dto) {
        if (staffId == null || dto.getStaffId() == null) {
            throw new BadRequestException(messageSource.getMessage(
                    INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION},
                    Locale.getDefault()));
        }

        StaffLoginAuditDto staffLoginAudit = this.getStaffLoginAuditById(staffId);

        staffLoginAudit.setLastLoginAt(dto.getLastLoginAt());
        staffLoginAudit.setLastLoginFailedAt(dto.getLastLoginFailedAt());
        staffLoginAudit.setLoginFailedAttempts(dto.getLoginFailedAttempts());
        staffLoginAudit.setLastForgotPasswordAt(dto.getLastForgotPasswordAt());
        staffLoginAudit.setForgotPasswordAttempts(dto.getForgotPasswordAttempts());
        staffLoginAudit.setLastResetPasswordAt(dto.getLastResetPasswordAt());

        return appMapper.toDto(staffLoginAuditRepository.save(appMapper.toEntity(staffLoginAudit)));
    }

    @Override
    @Transactional
    public StaffLoginAuditDto createStaffLoginAudit(StaffLoginAuditDto dto) {
        if (dto.getStaffId() == null) {
            throw new BadRequestException(messageSource.getMessage(
                    INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION},
                    Locale.getDefault()));
        }

        Optional<StaffLoginAuditDto> staffLoginAuditDto = this.findStaffLoginAuditById(dto.getStaffId());

        return staffLoginAuditDto.orElseGet(() -> appMapper.toDto(staffLoginAuditRepository.save(appMapper.toEntity(dto))));
    }

    @Override
    @Transactional
    public void deleteStaffLoginAudit(UUID staffID) {
        staffRepository.deleteById(staffID);
    }

    @Override
    @Transactional
    public void deleteAllByStaffIdIn(Set<UUID> staffIds) {
        staffRepository.deleteAllByIdInBatch(staffIds);
    }
}
