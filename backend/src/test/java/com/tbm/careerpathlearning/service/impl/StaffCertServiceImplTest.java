package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffCertDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffCert;
import com.tbm.careerpathlearning.repository.StaffCertRepository;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffCertServiceImplTest {

    @Mock private StaffCertRepository staffCertRepository;
    @Mock private MessageSource messageSource;
    @Mock private ValidationService validationService;
    @Mock private AppMapper appMapper;

    @InjectMocks
    private StaffCertServiceImpl staffCertService;

    private StaffCert mockCert;
    private StaffCertDto mockCertDto;
    private UUID staffId;
    private Long certId;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        certId = 1L;

        Staff staff = new Staff();
        staff.setId(staffId);
        StaffDto staffDto = new StaffDto();
        staffDto.setId(staffId);

        mockCert = new StaffCert();
        mockCert.setId(certId);
        mockCert.setStaff(staff);
        mockCert.setFileName("cert.pdf");

        mockCertDto = new StaffCertDto();
        mockCertDto.setId(certId);
        mockCertDto.setStaff(staffDto);
        mockCertDto.setFileName("cert.pdf");
        mockCertDto.setCertName("Java Cert");
        mockCertDto.setCertPath("/path/cert.pdf");

        lenient().when(messageSource.getMessage(any(), any(), any())).thenReturn("Error");
    }

    // --- Find Tests ---

    @Test
    void findById_ShouldReturnDto_WhenExists() {
        doReturn(Optional.of(mockCert)).when(staffCertRepository).findById(certId);
        when(appMapper.toDto(mockCert)).thenReturn(mockCertDto);

        assertNotNull(staffCertService.findById(certId));
    }

    @Test
    void findById_ShouldThrowException_WhenNotFound() {
        doReturn(Optional.empty()).when(staffCertRepository).findById(certId);
        assertThrows(BadRequestException.class, () -> staffCertService.findById(certId));
    }

    @Test
    void findByStaffId_ShouldReturnList() {
        when(staffCertRepository.findAllByStaff_Id(staffId)).thenReturn(List.of(mockCert));
        when(appMapper.toDto(mockCert)).thenReturn(mockCertDto);

        assertFalse(staffCertService.findByStaffId(staffId).isEmpty());
    }

    // --- Create Tests ---

    @Test
    void create_ShouldSave_WhenValidAndUnique() {
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);
        // Ensure uniqueness
        doReturn(Optional.empty()).when(staffCertRepository).findByStaff_IdAndFileNameIgnoreCase(staffId, "cert.pdf");

        when(appMapper.toEntity(mockCertDto)).thenReturn(mockCert);
        when(staffCertRepository.save(mockCert)).thenReturn(mockCert);
        when(appMapper.toDto(mockCert)).thenReturn(mockCertDto);

        assertNotNull(staffCertService.create(mockCertDto));
        verify(staffCertRepository).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenDuplicateFileName() {
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        doReturn(Optional.of(mockCert)).when(staffCertRepository).findByStaff_IdAndFileNameIgnoreCase(staffId, "cert.pdf");

        when(appMapper.toDto(mockCert)).thenReturn(mockCertDto);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));

        // Verify save was never called
        verify(staffCertRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenValidationFails() {
        mockCertDto.setFileName(""); // Invalid
        when(validationService.isNullOrBlank("")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    @Test
    void create_ShouldThrowException_WhenStaffIsNull() {
        mockCertDto.setStaff(null);
        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    @Test
    void create_ShouldThrowException_WhenCertPathIsBlank() {
        mockCertDto.setFileName("file.pdf");
        mockCertDto.setCertPath(""); // Blank

        // Mock: FileName is OK, but CertPath is BLANK
        when(validationService.isNullOrBlank("file.pdf")).thenReturn(false);
        when(validationService.isNullOrBlank("")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    @Test
    void create_ShouldThrowException_WhenCertNameTooLong() {
        String longName = "a".repeat(256); // Max 255
        mockCertDto.setCertName(longName);

        // Ensure other fields pass null checks
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    @Test
    void create_ShouldThrowException_WhenFileNameTooLong() {
        String longFileName = "a".repeat(260); // Max 259
        mockCertDto.setFileName(longFileName);

        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    @Test
    void create_ShouldThrowException_WhenCertPathTooLong() {
        String longPath = "a".repeat(1001); // Max 1000
        mockCertDto.setCertPath(longPath);

        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    @Test
    void create_ShouldThrowException_WhenDescriptionTooLong() {
        String longDesc = "a".repeat(1001); // Max 1000
        mockCertDto.setDescription(longDesc);

        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffCertService.create(mockCertDto));
    }

    // --- Update Tests ---

    @Test
    void update_ShouldSave_WhenValid() {
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        doReturn(Optional.of(mockCert)).when(staffCertRepository).findByStaff_IdAndFileNameIgnoreCase(staffId, "cert.pdf");

        when(appMapper.toDto(mockCert)).thenReturn(mockCertDto);

        when(appMapper.toEntity(any(StaffCertDto.class))).thenReturn(mockCert);
        when(staffCertRepository.save(any())).thenReturn(mockCert);

        assertNotNull(staffCertService.update(mockCertDto));
    }

    @Test
    void update_ShouldThrowException_WhenDuplicateNameExistsWithDifferentId() {
        when(validationService.isNullOrBlank(anyString())).thenReturn(false);

        // Create a DIFFERENT Entity with a different ID
        StaffCert otherCertEntity = new StaffCert();
        otherCertEntity.setId(999L); // Different ID from mockCertDto (which is 1L)

        // Create the matching DTO for that entity
        StaffCertDto otherCertDto = new StaffCertDto();
        otherCertDto.setId(999L);

        doReturn(Optional.of(otherCertEntity)).when(staffCertRepository).findByStaff_IdAndFileNameIgnoreCase(staffId, "cert.pdf");

        // Mock the mapper to convert that entity to the DTO
        when(appMapper.toDto(otherCertEntity)).thenReturn(otherCertDto);

        assertThrows(BadRequestException.class, () -> staffCertService.update(mockCertDto));
    }

    // --- Delete Tests ---

    @Test
    void findAndDeleteById_ShouldReturnDeletedDto() {
        doReturn(Optional.of(mockCert)).when(staffCertRepository).findById(certId);
        when(appMapper.toDto(mockCert)).thenReturn(mockCertDto);

        StaffCertDto result = staffCertService.findAndDeleteById(certId);

        assertNotNull(result);
        verify(staffCertRepository).deleteById(certId);
    }

    @Test
    void findAndDeleteByIdIn_ShouldDeleteAll() {
        Set<Long> ids = Set.of(1L, 2L);
        when(staffCertRepository.findAllById(ids)).thenReturn(List.of(mockCert));

        staffCertService.findAndDeleteByIdIn(ids);

        verify(staffCertRepository).deleteAll(anyList());
    }
}