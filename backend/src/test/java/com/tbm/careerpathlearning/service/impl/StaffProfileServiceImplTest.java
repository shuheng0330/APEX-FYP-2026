package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffProfileDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffProfile;
import com.tbm.careerpathlearning.repository.StaffProfileRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffProfileServiceImplTest {

    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private ValidationService validationService;
    @Mock
    private AppMapper appMapper;
    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private StaffProfileServiceImpl staffProfileService;

    private StaffProfile mockProfile;
    private StaffProfileDto mockProfileDto;
    private Staff mockStaff;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();

        mockStaff = new Staff();
        mockStaff.setId(staffId);
        mockStaff.setEmail("test@test.com");

        mockProfile = new StaffProfile();
        mockProfile.setStaffId(staffId);
        mockProfile.setStaff(mockStaff);

        mockProfileDto = new StaffProfileDto();
        mockProfileDto.setStaffId(staffId);
        mockProfileDto.setContactNumber("1234567890");

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Error Message");
    }

    @Test
    void findById_ShouldReturnDto_WhenExists() {
        doReturn(Optional.of(mockProfile)).when(staffProfileRepository).findById(staffId);
        when(appMapper.toDto(mockProfile)).thenReturn(mockProfileDto);
        assertNotNull(staffProfileService.findById(staffId));
    }

    @Test
    void findById_ShouldThrowException_WhenNotFound() {
        doReturn(Optional.empty()).when(staffProfileRepository).findById(staffId);
        assertThrows(BadRequestException.class, () -> staffProfileService.findById(staffId));
    }

    @Test
    void findAll_ShouldReturnList() {
        when(staffProfileRepository.findAll()).thenReturn(List.of(mockProfile));
        when(appMapper.toDto(mockProfile)).thenReturn(mockProfileDto);
        assertFalse(staffProfileService.findAll().isEmpty());
    }

    @Test
    void create_ShouldSave_WhenIdIsUniqueAndValid() {
        doReturn(Optional.empty()).when(staffProfileRepository).findById(staffId);
        doReturn(Optional.of(new Staff())).when(staffRepository).findById(staffId);
        when(appMapper.toEntity(mockProfileDto)).thenReturn(mockProfile);
        when(staffProfileRepository.save(any())).thenReturn(mockProfile);
        when(appMapper.toDto(mockProfile)).thenReturn(mockProfileDto);

        assertNotNull(staffProfileService.create(mockProfileDto));
    }

    @Test
    void create_ShouldThrowException_WhenProfileAlreadyExists() {
        doReturn(Optional.of(mockProfile)).when(staffProfileRepository).findById(staffId);
        when(appMapper.toDto(mockProfile)).thenReturn(mockProfileDto);
        assertThrows(BadRequestException.class, () -> staffProfileService.create(mockProfileDto));
    }

    @Test
    void create_ShouldThrowException_WhenStaffIdIsNull() {
        mockProfileDto.setStaffId(null);
        assertThrows(BadRequestException.class, () -> staffProfileService.create(mockProfileDto));
    }

    @Test
    void create_ShouldThrowException_WhenAboutTooLong() {
        mockProfileDto.setStaffId(staffId);
        mockProfileDto.setAbout("a".repeat(1001));

        assertThrows(BadRequestException.class, () -> staffProfileService.create(mockProfileDto));
    }

    @Test
    void createAll_ShouldCreateProfiles_WhenValid() {
        // 1. Mock existing profiles (empty list)
        when(staffProfileRepository.findAll()).thenReturn(Collections.emptyList());

        // 2. Mock existing staff (valid staff exists)
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        // 3. Mock Mapping
        when(appMapper.toEntity(mockProfileDto)).thenReturn(mockProfile);
        when(appMapper.toDto(mockProfile)).thenReturn(mockProfileDto);

        // 4. Mock Saving
        when(staffProfileRepository.saveAll(anyList())).thenReturn(List.of(mockProfile));

        List<StaffProfileDto> result = staffProfileService.createAll(List.of(mockProfileDto));

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(staffProfileRepository).saveAll(anyList());
    }

    @Test
    void createAll_ShouldThrowBadRequest_WhenListIsEmpty() {
        List<StaffProfileDto> emptyList = Collections.emptyList();
        assertThrows(BadRequestException.class, () -> staffProfileService.createAll(emptyList));
        verify(staffProfileRepository, never()).saveAll(anyList());
    }

    @Test
    void createAll_ShouldThrowBadRequest_WhenStaffIdIsNull() {
        when(staffProfileRepository.findAll()).thenReturn(Collections.emptyList());
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        mockProfileDto.setStaffId(null);
        List<StaffProfileDto> input = List.of(mockProfileDto);

        assertThrows(BadRequestException.class, () -> staffProfileService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowBadRequest_WhenProfileAlreadyExists() {
        // Simulate existing profile
        when(staffProfileRepository.findAll()).thenReturn(List.of(mockProfile));
        when(appMapper.toDto(mockProfile)).thenReturn(mockProfileDto); // findAll returns DTOs via mapper

        List<StaffProfileDto> input = List.of(mockProfileDto);

        assertThrows(BadRequestException.class, () -> staffProfileService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowBadRequest_WhenAboutIsTooLong() {
        when(staffProfileRepository.findAll()).thenReturn(Collections.emptyList());
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        mockProfileDto.setAbout("a".repeat(1001));
        List<StaffProfileDto> input = List.of(mockProfileDto);

        assertThrows(BadRequestException.class, () -> staffProfileService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowBadRequest_WhenContactIsTooLong() {
        when(staffProfileRepository.findAll()).thenReturn(Collections.emptyList());
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        mockProfileDto.setContactNumber("1".repeat(16));
        List<StaffProfileDto> input = List.of(mockProfileDto);

        assertThrows(BadRequestException.class, () -> staffProfileService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowBadRequest_WhenProfilePathIsTooLong() {
        when(staffProfileRepository.findAll()).thenReturn(Collections.emptyList());
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockStaff));

        mockProfileDto.setProfilePicturePath("a".repeat(1001));
        List<StaffProfileDto> input = List.of(mockProfileDto);

        assertThrows(BadRequestException.class, () -> staffProfileService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowNPE_WhenStaffEntityNotFound() {
        when(staffProfileRepository.findAll()).thenReturn(Collections.emptyList());
        when(staffRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList()); // No staff found
        when(appMapper.toEntity(mockProfileDto)).thenReturn(mockProfile);

        List<StaffProfileDto> input = List.of(mockProfileDto);

        assertThrows(NullPointerException.class, () -> staffProfileService.createAll(input));
    }

    @Test
    void update_ShouldSave_WhenDataValid() {
        mockProfileDto.setContactNumber("0999999999");
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        doReturn(Optional.of(mockProfile)).when(staffProfileRepository).findById(staffId);
        when(appMapper.toDto(any(StaffProfile.class))).thenReturn(mockProfileDto);
        when(appMapper.toEntity(any(StaffProfileDto.class))).thenReturn(mockProfile);
        when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(mockProfile);

        assertNotNull(staffProfileService.update(staffId, mockProfileDto));
        verify(staffProfileRepository).save(any());
    }

    @Test
    void update_ShouldThrowException_WhenContactNumberIsBlank() {
        mockProfileDto.setContactNumber("");
        when(validationService.isNullOrBlank("")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> staffProfileService.update(staffId, mockProfileDto));
    }

    @Test
    void updateProfilePicture_ShouldSave_WhenPathValid() {
        mockProfileDto.setProfilePicturePath("/path/pic.png");
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        doReturn(Optional.of(mockProfile)).when(staffProfileRepository).findById(staffId);
        when(appMapper.toDto(any(StaffProfile.class))).thenReturn(mockProfileDto);
        when(appMapper.toEntity(any(StaffProfileDto.class))).thenReturn(mockProfile);
        when(staffProfileRepository.save(any())).thenReturn(mockProfile);

        assertNotNull(staffProfileService.updateProfilePicture(staffId, mockProfileDto));
    }

    @Test
    void update_ShouldThrowException_WhenStaffIdIsNull() {
        assertThrows(BadRequestException.class, () -> staffProfileService.update(null, mockProfileDto));
    }

    @Test
    void update_ShouldThrowException_WhenContactNumberTooLong() {
        mockProfileDto.setContactNumber("1234567890123456"); // 16 chars (Max 15)
        // Ensure validationService returns false for isNullOrBlank so it hits the length check
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffProfileService.update(staffId, mockProfileDto));
    }

    @Test
    void update_ShouldThrowException_WhenProfilePicPathTooLong() {
        String longPath = "a".repeat(1001);
        mockProfileDto.setProfilePicturePath(longPath);
        mockProfileDto.setContactNumber("123"); // Valid contact
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffProfileService.update(staffId, mockProfileDto));
    }

    @Test
    void update_ShouldThrowException_WhenAboutIsTooLong() {
        String longText = "a".repeat(1001); // 1001 chars
        mockProfileDto.setAbout(longText);
        mockProfileDto.setContactNumber("123");

        assertThrows(BadRequestException.class, () -> staffProfileService.update(staffId, mockProfileDto));
    }

    @Test
    void updateProfilePicture_ShouldThrowException_WhenStaffIdIsNull() {
        assertThrows(BadRequestException.class, () -> staffProfileService.updateProfilePicture(null, mockProfileDto));
    }

    @Test
    void updateProfilePicture_ShouldThrowException_WhenPathIsBlank() {
        mockProfileDto.setProfilePicturePath("");
        when(validationService.isNullOrBlank("")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> staffProfileService.updateProfilePicture(staffId, mockProfileDto));
    }

    @Test
    void updateProfilePicture_ShouldThrowException_WhenPathTooLong() {
        String longPath = "a".repeat(1001);
        mockProfileDto.setProfilePicturePath(longPath);
        when(validationService.isNullOrBlank(longPath)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> staffProfileService.updateProfilePicture(staffId, mockProfileDto));
    }

    @Test
    void delete_ShouldCallRepositoryDelete() {
        staffProfileService.delete(staffId);
        verify(staffProfileRepository).deleteById(staffId);
    }

}