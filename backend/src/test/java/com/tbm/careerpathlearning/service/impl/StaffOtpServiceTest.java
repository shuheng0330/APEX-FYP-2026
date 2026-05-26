package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffOtpDto;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffOtp;
import com.tbm.careerpathlearning.repository.StaffOtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffOtpServiceTest {

    @Mock
    private MessageSource messageSource;
    @Mock
    private AppMapper appMapper;
    @Mock
    private StaffOtpRepository staffOtpRepository;

    @InjectMocks
    private StaffOtpServiceImpl staffOtpService;

    private StaffDto mockStaffDto;
    private Staff mockStaffEntity;
    private StaffOtpDto mockOtpDto;
    private StaffOtp mockOtpEntity;
    private final Long OTP_ID = 1L;
    private final String VALID_OTP = "123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(staffOtpService, "EXPIRY_MINUTES", 5);

        mockStaffDto = new StaffDto();
        mockStaffDto.setId(UUID.randomUUID());

        mockStaffEntity = new Staff();
        mockStaffEntity.setId(mockStaffDto.getId());

        mockOtpDto = new StaffOtpDto();
        mockOtpDto.setId(OTP_ID);
        mockOtpDto.setOtpCode(VALID_OTP);
        mockOtpDto.setStaff(mockStaffDto);
        mockOtpDto.setOtpPurpose(OtpPurpose.PASSWORD_RESET);
        mockOtpDto.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        mockOtpDto.setUsed(false);

        mockOtpEntity = new StaffOtp();
        mockOtpEntity.setId(OTP_ID);
        mockOtpEntity.setOtpCode(VALID_OTP);
        mockOtpEntity.setStaff(mockStaffEntity);
        mockOtpEntity.setOtpPurpose(OtpPurpose.PASSWORD_RESET);
        mockOtpDto.setExpiresAt(mockOtpDto.getExpiresAt());
        mockOtpDto.setUsed(false);
    }

    @Test
    void generateOtp_ShouldReturnSixDigits() {
        String otp = staffOtpService.generateOtp();
        assertThat(otp).hasSize(6).containsOnlyDigits();
    }

    @Test
    void getStaffOtpById_NotFound_ShouldThrowException() {
        when(staffOtpRepository.findById(OTP_ID)).thenReturn(Optional.empty());
        when(messageSource.getMessage(any(), any(), any())).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> staffOtpService.getStaffOtpById(OTP_ID));
    }

    @Test
    void getAllStaffOtps_ShouldReturnList() {
        when(staffOtpRepository.findAll()).thenReturn(List.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        List<StaffOtpDto> result = staffOtpService.getAllStaffOtps();

        assertThat(result).hasSize(1);
        verify(staffOtpRepository).findAll();
    }

    @Test
    void createStaffOtp_Valid_ShouldReturnDto() {
        when(appMapper.toEntity(any(StaffOtpDto.class))).thenReturn(mockOtpEntity);
        when(staffOtpRepository.save(any())).thenReturn(mockOtpEntity);
        when(appMapper.toDto(any(StaffOtp.class))).thenReturn(mockOtpDto);

        StaffOtpDto result = staffOtpService.createStaffOtp(mockStaffDto, OtpPurpose.PASSWORD_RESET);

        assertThat(result).isNotNull();
        verify(staffOtpRepository).save(any());
    }

    @Test
    void createStaffOtp_NullParams_ShouldThrowException() {
        assertThrows(BadRequestException.class, () -> staffOtpService.createStaffOtp(null, null));
    }

    @Test
    void validateOtp_Valid_ShouldReturnTrueAndDelete() {
        when(appMapper.toEntity(mockStaffDto)).thenReturn(mockStaffEntity);
        when(staffOtpRepository.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        boolean isValid = staffOtpService.validateOtp(mockStaffDto, VALID_OTP, OtpPurpose.PASSWORD_RESET);

        assertTrue(isValid);
        verify(staffOtpRepository).deleteById(OTP_ID);
    }

    @Test
    void validateOtp_NotFound_ShouldThrowException() {
        when(appMapper.toEntity(mockStaffDto)).thenReturn(mockStaffEntity);
        // Simulate repository returning empty
        when(staffOtpRepository.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenRequestException.class, () ->
                staffOtpService.validateOtp(mockStaffDto, VALID_OTP, OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void validateOtp_AlreadyUsed_ShouldThrowException() {
        mockOtpDto.setUsed(true); // Mark as used

        when(appMapper.toEntity(mockStaffDto)).thenReturn(mockStaffEntity);
        when(staffOtpRepository.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        assertThrows(ForbiddenRequestException.class, () ->
                staffOtpService.validateOtp(mockStaffDto, VALID_OTP, OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void validateOtp_Expired_ShouldThrowException() {
        mockOtpDto.setExpiresAt(OffsetDateTime.now().minusMinutes(1)); // Expired

        when(appMapper.toEntity(mockStaffDto)).thenReturn(mockStaffEntity);
        when(staffOtpRepository.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        assertThrows(ForbiddenRequestException.class, () ->
                staffOtpService.validateOtp(mockStaffDto, VALID_OTP, OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void validateOtp_WrongCode_ShouldThrowException() {
        when(appMapper.toEntity(mockStaffDto)).thenReturn(mockStaffEntity);
        when(staffOtpRepository.findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        assertThrows(ForbiddenRequestException.class, () ->
                staffOtpService.validateOtp(mockStaffDto, "000000", OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void updateStaffOtp_NullFields_ShouldThrowBadRequest() {
        StaffOtpDto invalidDto = new StaffOtpDto();
        assertThrows(BadRequestException.class, () -> staffOtpService.updateStaffOtp(OTP_ID, invalidDto));
    }

    @Test
    void updateStaffOtp_Valid_ShouldReturnUpdatedDto() {
        // Arrange
        // 1. Initial fetch inside the method
        when(staffOtpRepository.findById(OTP_ID)).thenReturn(Optional.of(mockOtpEntity));

        // 2. Mapping entity to DTO for the update process
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        // 3. Mapping DTO back to entity for saving
        when(appMapper.toEntity(any(StaffOtpDto.class))).thenReturn(mockOtpEntity);

        // 4. The save operation
        when(staffOtpRepository.save(any(StaffOtp.class))).thenReturn(mockOtpEntity);

        // Act
        StaffOtpDto result = staffOtpService.updateStaffOtp(OTP_ID, mockOtpDto);

        // Assert
        assertThat(result).isNotNull();
        verify(staffOtpRepository).save(any(StaffOtp.class));
        // Verify toDto was called at least once (for the initial get or the final return)
        verify(appMapper, atLeastOnce()).toDto(any(StaffOtp.class));
    }

    @Test
    void getStaffOtpByStaffId_ShouldReturnList() {
        UUID staffId = mockStaffDto.getId();
        when(staffOtpRepository.findAllByStaff_Id(staffId)).thenReturn(List.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        List<StaffOtpDto> result = staffOtpService.getStaffOtpByStaffId(staffId);

        assertThat(result).hasSize(1);
        verify(staffOtpRepository).findAllByStaff_Id(staffId);
    }

    @Test
    void getStaffOtpByStaffIdIn_ShouldReturnList() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(staffOtpRepository.findAllByStaffIdIn(ids)).thenReturn(List.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        List<StaffOtpDto> result = staffOtpService.getStaffOtpByStaffIdIn(ids);

        assertThat(result).hasSize(1);
    }

    @Test
    void getStaffOtpByOtpCode_ShouldReturnOptional() {
        when(staffOtpRepository.getByOtpCode(VALID_OTP)).thenReturn(Optional.of(mockOtpEntity));
        when(appMapper.toDto(mockOtpEntity)).thenReturn(mockOtpDto);

        Optional<StaffOtpDto> result = staffOtpService.getStaffOtpByOtpCode(VALID_OTP);

        assertThat(result).isPresent();
        assertThat(result.get().getOtpCode()).isEqualTo(VALID_OTP);
    }

    @Test
    void deleteAllByIdIn_ShouldCallRepository() {
        Set<Long> ids = Set.of(1L, 2L);
        staffOtpService.deleteAllByIdIn(ids);
        verify(staffOtpRepository).deleteAllByIdInBatch(ids);
    }
}