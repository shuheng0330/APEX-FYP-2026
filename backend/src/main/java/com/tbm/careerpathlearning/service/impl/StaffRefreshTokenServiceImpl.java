package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffRefreshTokenDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.repository.StaffRefreshTokenRepository;
import com.tbm.careerpathlearning.service.StaffRefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StaffRefreshTokenServiceImpl implements StaffRefreshTokenService {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private StaffRefreshTokenRepository staffRefreshTokenRepository;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UPDATE_OPERATION = "Update Refresh Token";

    private static final String CREATE_OPERATION = "Create Refresh Token";

    @Override
    public List<StaffRefreshTokenDto> getAllStaffRefreshTokens() {
        return staffRefreshTokenRepository.findAll().stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public StaffRefreshTokenDto getStaffRefreshTokenById(UUID id) {
        return staffRefreshTokenRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public Optional<StaffRefreshTokenDto> findStaffRefreshTokenById(UUID id) {
        return staffRefreshTokenRepository.findById(id).map(appMapper::toDto);
    }

    @Override
    public Optional<StaffRefreshTokenDto> getStaffRefreshTokenByToken(String token) {
        return staffRefreshTokenRepository.getByToken(token).map(appMapper::toDto);
    }

    @Override
    public List<StaffRefreshTokenDto> getStaffRefreshTokenByIdIn(Set<UUID> ids) {
        return staffRefreshTokenRepository.findAllById(ids).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StaffRefreshTokenDto updateStaffRefreshToken(UUID staffId, StaffRefreshTokenDto dto) {
        if (staffId == null || dto.getToken() == null || dto.getExpiresAt() == null) {
            throw new BadRequestException(messageSource.getMessage(
                    INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION},
                    Locale.getDefault()));
        }

        StaffRefreshTokenDto staffRefreshTokenDto = this.getStaffRefreshTokenById(staffId);

        staffRefreshTokenDto.setToken(dto.getToken());
        staffRefreshTokenDto.setCreatedAt(dto.getCreatedAt());
        staffRefreshTokenDto.setExpiresAt(dto.getExpiresAt());

        return appMapper.toDto(staffRefreshTokenRepository.save(appMapper.toEntity(staffRefreshTokenDto)));
    }

    @Override
    @Transactional
    public StaffRefreshTokenDto createStaffRefreshToken(StaffRefreshTokenDto dto) {
        if (dto.getStaffId() == null) {
            throw new BadRequestException(messageSource.getMessage(
                    INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION},
                    Locale.getDefault()));
        }

        Optional<StaffRefreshTokenDto> staffRefreshTokenDto = this.findStaffRefreshTokenById(dto.getStaffId());

        return staffRefreshTokenDto.orElseGet(() -> appMapper.toDto(staffRefreshTokenRepository.save(appMapper.toEntity(dto))));
    }

    @Override
    @Transactional
    public void deleteStaffRefreshToken(UUID staffID) {
        staffRefreshTokenRepository.deleteById(staffID);
    }

    @Override
    @Transactional
    public void deleteAllByStaffIdIn(Set<UUID> staffIds) {
        staffRefreshTokenRepository.deleteAllByIdInBatch(staffIds);
    }
}
