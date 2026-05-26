package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.MaterialProgressDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffLearningMaterialDto;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffLearningMaterialServiceImplTest {

    @Mock private StaffLearningMaterialRepository staffLearningMaterialRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private LearningMaterialRepository learningMaterialRepository;
    @Mock private StaffLearningDocumentRepository staffLearningDocumentRepository;
    @Mock private AppMapper appMapper;

    @InjectMocks
    private StaffLearningMaterialServiceImpl service;
    private final UUID STAFF_ID = UUID.randomUUID();
    private Staff mockStaff;
    private LearningMaterial mockMaterial;
    private StaffLearningMaterialDto enrollmentDto;
    private StaffLearningMaterial sampleEnrollment;
    private final Long MATERIAL_ID = 1L;

    @BeforeEach
    void setUp() {
        mockStaff = new Staff();
        mockStaff.setId(UUID.randomUUID());

        sampleEnrollment = new StaffLearningMaterial();
        sampleEnrollment.setEnrollmentId(10L);
        sampleEnrollment.setProgress(50.0);

        mockMaterial = new LearningMaterial();
        mockMaterial.setMaterialId(1L);
        mockMaterial.setLearningDocuments(List.of(new LearningDocument(), new LearningDocument()));

        enrollmentDto = new StaffLearningMaterialDto();
        enrollmentDto.setStaffId(mockStaff.getId());
        enrollmentDto.setMaterialId(1L);
    }

    @Test
    @DisplayName("Enroll - Success: Should create enrollment and initialize document progress")
    void enroll_Success() {
        // Arrange
        when(staffRepository.findById(any())).thenReturn(Optional.of(mockStaff));
        when(learningMaterialRepository.findById(any())).thenReturn(Optional.of(mockMaterial));
        when(staffLearningMaterialRepository.existsByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(false);
        when(appMapper.toDto(any(StaffLearningMaterial.class))).thenReturn(new StaffLearningMaterialDto());

        // Act
        StaffLearningMaterialDto result = service.enroll(enrollmentDto);

        // Assert
        assertNotNull(result);
        // Verify enrollment was saved
        verify(staffLearningMaterialRepository, times(1)).save(any(StaffLearningMaterial.class));
        // Verify progress was initialized for BOTH documents in the list
        verify(staffLearningDocumentRepository, times(2)).save(any(StaffLearningDocumentProgress.class));
    }

    @Test
    @DisplayName("Enroll - Failure: Should throw exception if enrollment already exists")
    void enroll_AlreadyExists_ThrowsException() {
        // Arrange
        when(staffRepository.findById(any())).thenReturn(Optional.of(mockStaff));
        when(learningMaterialRepository.findById(any())).thenReturn(Optional.of(mockMaterial));
        when(staffLearningMaterialRepository.existsByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(true);

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.enroll(enrollmentDto));
        assertEquals("Enrollment already exist", ex.getMessage());
    }

    @Test
    @DisplayName("Update Progress - Should mark as completed when all documents are 100%")
    void updateMaterialProgress_AllCompleted_SetsStatus() {
        // Arrange
        Long enrollmentId = 10L;
        StaffLearningMaterial slm = new StaffLearningMaterial();
        slm.setEnrollmentId(enrollmentId);

        StaffLearningDocumentProgress doc1 = new StaffLearningDocumentProgress();
        doc1.setProgress(100.0);
        StaffLearningDocumentProgress doc2 = new StaffLearningDocumentProgress();
        doc2.setProgress(100.0);

        when(staffLearningMaterialRepository.findById(enrollmentId)).thenReturn(Optional.of(slm));
        when(staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentId(enrollmentId))
                .thenReturn(List.of(doc1, doc2));

        // Act
        service.updateMaterialProgress(enrollmentId, 100.0);

        // Assert
        assertTrue(slm.isCompleted());
        assertNotNull(slm.getCompletedAt());
        assertEquals(100.0, slm.getProgress());
        verify(staffLearningMaterialRepository).save(slm);
    }

    @Test
    @DisplayName("Unenroll - Should delete both document progress and enrollment")
    void unenroll_Success() {
        // Arrange
        UUID staffId = UUID.randomUUID();
        Long matId = 1L;
        StaffLearningMaterial slm = new StaffLearningMaterial();

        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(staffId, matId))
                .thenReturn(Optional.of(slm));

        // Act
        service.unenroll(staffId, matId);

        // Assert
        verify(staffLearningDocumentRepository).deleteByStaffLearningMaterial(slm);
        verify(staffLearningMaterialRepository).delete(slm);
    }

    @Test
    @DisplayName("getEnrolledCourses - Should return mapped DTOs")
    void getEnrolledCourses_Success() {
        // Targets 0% method: getEnrolledCourses(UUID)
        when(staffLearningMaterialRepository.findLearningMaterialsByStaffId(STAFF_ID))
                .thenReturn(List.of(new LearningMaterial()));
        when(appMapper.toDto(any(LearningMaterial.class))).thenReturn(new LearningMaterialDto());

        List<LearningMaterialDto> result = service.getEnrolledCourses(STAFF_ID);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getProgressList - Should handle null progress as 0.0")
    void getProgressList_Success() {
        // 1. Create the dependency
        LearningMaterial material = new LearningMaterial();
        material.setMaterialId(MATERIAL_ID);

        // 2. Link it to the enrollment (This prevents the NPE)
        sampleEnrollment.setLearningMaterial(material);
        sampleEnrollment.setProgress(null);

        when(staffLearningMaterialRepository.findByStaffId(STAFF_ID))
                .thenReturn(List.of(sampleEnrollment));

        // 3. Execute
        List<MaterialProgressDto> result = service.getProgressList(STAFF_ID);

        // 4. Verify
        assertEquals(0.0, result.get(0).getProgress());
    }
    @Test
    @DisplayName("getLearningMaterialByStaffId - Should return mapped DTO")
    void getLearningMaterialByStaffId_Success() {
        // Targets 0% method: getLearningMaterialByStaffId(UUID, Long)
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(STAFF_ID, MATERIAL_ID))
                .thenReturn(Optional.of(sampleEnrollment));
        when(appMapper.toDto(sampleEnrollment)).thenReturn(new StaffLearningMaterialDto());

        StaffLearningMaterialDto result = service.getLearningMaterialByStaffId(STAFF_ID, MATERIAL_ID);

        assertNotNull(result);
    }

    @Test
    @DisplayName("updateMaterialProgress - Should handle empty document progress")
    void updateMaterialProgress_EmptyDocuments() {
        // Targets the 50% branch coverage of updateMaterialProgress
        when(staffLearningMaterialRepository.findById(10L)).thenReturn(Optional.of(sampleEnrollment));
        when(staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentId(10L))
                .thenReturn(Collections.emptyList());

        service.updateMaterialProgress(10L, 100.0);

        assertEquals(0.0, sampleEnrollment.getProgress());
        assertFalse(sampleEnrollment.isCompleted());
        verify(staffLearningMaterialRepository).save(sampleEnrollment);
    }

    @Test
    @DisplayName("getEnrollmentsByMaterialId - Should return list of staff progress")
    void getEnrollmentsByMaterialId_Success() {
        // 1. Create and initialize the Staff entity
        Staff staff = new Staff();
        staff.setId(STAFF_ID);
        staff.setName("John Doe");

        // 2. Link the staff to the enrollment record
        sampleEnrollment.setStaff(staff);

        // 3. Stub the mapper to expect this specific staff instance
        when(staffLearningMaterialRepository.findByMaterialId(MATERIAL_ID))
                .thenReturn(List.of(sampleEnrollment));
        when(appMapper.toDto(staff)).thenReturn(new StaffDto()); // Matches the actual object passed

        // 4. Execute
        List<StaffLearningMaterialDto> result = service.getEnrollmentsByMaterialId(MATERIAL_ID);

        // 5. Verify
        assertNotNull(result);
        verify(appMapper).toDto(staff);
    }
}