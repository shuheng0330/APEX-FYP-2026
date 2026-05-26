package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffOtpDto;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.repository.StaffOtpRepository;
import com.tbm.careerpathlearning.service.StaffOtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StaffOtpServiceImpl implements StaffOtpService {

    @Value("${otp.expiry.minutes}")
    private int EXPIRY_MINUTES;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private StaffOtpRepository staffOtpRepository;

    private static final SecureRandom random = new SecureRandom();

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String OTP_INVALID_ERR_TITLE_CODE = "otp.invalid.err.title";

    private static final String OTP_INVALID_ERR_MSG_CODE = "otp.invalid.err.msg";

    private static final String CREATE_OPERATION = "Create OTP";

    private static final String UPDATE_OPERATION = "Update OTP";

    @Override
    public String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public List<StaffOtpDto> getAllStaffOtps() {
        return staffOtpRepository.findAll().stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public StaffOtpDto getStaffOtpById(Long id) {
        return staffOtpRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public List<StaffOtpDto> getStaffOtpByStaffId(UUID id) {
        return staffOtpRepository.findAllByStaff_Id(id).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public Optional<StaffOtpDto> getStaffOtpByOtpCode(String otpCode) {
        return staffOtpRepository.getByOtpCode(otpCode).map(appMapper::toDto);
    }

    @Override
    public List<StaffOtpDto> getStaffOtpByStaffIdIn(Set<UUID> ids) {
        return staffOtpRepository.findAllByStaffIdIn(ids).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StaffOtpDto updateStaffOtp(Long id, StaffOtpDto dto) {
        if (id == null || dto.getStaff() == null || dto.getOtpCode() == null || dto.getOtpPurpose() == null || dto.getExpiresAt() == null) {
            throw new BadRequestException(messageSource.getMessage(
                    INVALID_DATA_ERR_MSG_CODE,
                    new String[]{UPDATE_OPERATION},
                    Locale.getDefault()));
        }

        StaffOtpDto staffOtpDto = this.getStaffOtpById(id);

        staffOtpDto.setOtpCode(dto.getOtpCode());
        staffOtpDto.setOtpPurpose(dto.getOtpPurpose());
        staffOtpDto.setUsed(dto.isUsed());
        staffOtpDto.setCreatedAt(dto.getCreatedAt());
        staffOtpDto.setExpiresAt(dto.getExpiresAt());

        return appMapper.toDto(staffOtpRepository.save(appMapper.toEntity(staffOtpDto)));
    }

    @Override
    @Transactional
    public StaffOtpDto createStaffOtp(StaffDto staffDto, OtpPurpose otpPurpose) {
        if (staffDto == null || otpPurpose == null) {
            throw new BadRequestException(messageSource.getMessage(
                    INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION},
                    Locale.getDefault()));
        }

        String otpCode = this.generateOtp();

        StaffOtpDto staffOtpDto = new StaffOtpDto();
        staffOtpDto.setStaff(staffDto);
        staffOtpDto.setOtpCode(otpCode);
        staffOtpDto.setOtpPurpose(otpPurpose);
        staffOtpDto.setUsed(false);
        staffOtpDto.setCreatedAt(OffsetDateTime.now());
        staffOtpDto.setExpiresAt(OffsetDateTime.now().plusMinutes(EXPIRY_MINUTES));

        return appMapper.toDto(staffOtpRepository.save(appMapper.toEntity(staffOtpDto)));
    }

    @Override
    @Transactional
    public boolean validateOtp(StaffDto staffDto, String otp, OtpPurpose otpPurpose) {
        Optional<StaffOtpDto> staffOtpDto = this.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(staffDto, otpPurpose);

        if (staffOtpDto.isEmpty() || staffOtpDto.get().isUsed() || OffsetDateTime.now().isAfter(staffOtpDto.get().getExpiresAt())
                || !staffOtpDto.get().getOtpCode().equals(otp)) {
            String errorTitle = messageSource.getMessage(OTP_INVALID_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(OTP_INVALID_ERR_MSG_CODE, null, Locale.getDefault());

            throw new ForbiddenRequestException(errorTitle, errorMessage);
        }

        this.deleteStaffOtp(staffOtpDto.get().getId());

        return true;
    }

    @Override
    public Optional<StaffOtpDto> findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc
            (StaffDto staff, OtpPurpose purpose) {
        return staffOtpRepository.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc
                (appMapper.toEntity(staff), purpose).map(appMapper::toDto);
    }

    @Override
    @Transactional
    public void deleteStaffOtp(Long id) {
        staffOtpRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteAllByIdIn(Set<Long> ids) {
        staffOtpRepository.deleteAllByIdInBatch(ids);
    }
}
