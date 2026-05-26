package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffLearningDocumentProgressDto;
import com.tbm.careerpathlearning.dto.UpdateDocumentProgressDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.LearningDocument;
import com.tbm.careerpathlearning.model.StaffLearningDocumentProgress;
import com.tbm.careerpathlearning.model.StaffLearningMaterial;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffLearningDocumentServiceImplTest {

    @Mock private StaffLearningDocumentRepository staffLearningDocumentRepository;
    @Mock private StaffLearningMaterialRepository staffLearningMaterialRepository;
    @Mock private LearningDocumentRepository learningDocumentRepository;
    @Mock private StaffLearningMaterialServiceImpl staffLearningMaterialServiceImpl;
    @Mock private MessageSource messageSource;
    @Mock private AppMapper appMapper;

    @InjectMocks
    private StaffLearningDocumentServiceImpl staffLearningDocumentService;

    private UpdateDocumentProgressDto updateDto;
    private StaffLearningMaterial mockSlm;
    private LearningDocument mockDoc;

    @BeforeEach
    void setUp() {
        UUID staffId = UUID.randomUUID();
        Long materialId = 1L;
        Long documentId = 100L;

        updateDto = new UpdateDocumentProgressDto();
        updateDto.setStaffId(staffId);
        updateDto.setMaterialId(materialId);
        updateDto.setDocumentId(documentId);
        updateDto.setProgress(50.0);
        updateDto.setIsCompleted(false);
        updateDto.setOverallProgress(10.0);

        mockSlm = new StaffLearningMaterial();
        mockSlm.setEnrollmentId(500L);

        mockDoc = new LearningDocument();
        mockDoc.setDocumentId(documentId);
    }

    @Test
    @DisplayName("updateDocumentProgress - Should throw exception if staff not enrolled")
    void updateDocumentProgress_NotEnrolled_ThrowsException() {
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.empty());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Not enrolled");

        assertThrows(BadRequestException.class, () -> staffLearningDocumentService.updateDocumentProgress(updateDto));
    }

    @Test
    @DisplayName("updateDocumentProgress - Should throw DataAccessException on save failure")
    void updateDocumentProgress_SaveFailure_ThrowsDataAccessException() {
        // Arrange
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.of(mockSlm));
        when(learningDocumentRepository.findById(anyLong())).thenReturn(Optional.of(mockDoc));
        when(staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentIdAndLearningDocument_DocumentId(anyLong(), anyLong()))
                .thenReturn(Optional.of(new StaffLearningDocumentProgress()));

        // Simulate DB error
        doThrow(new RuntimeException("DB Error")).when(staffLearningDocumentRepository).save(any());

        // Act & Assert
        assertThrows(com.tbm.careerpathlearning.exception.DataAccessException.class,
                () -> staffLearningDocumentService.updateDocumentProgress(updateDto));
    }

    @Test
    @DisplayName("updateDocumentProgress - Should set progress to 100% if isCompleted is true")
    void updateDocumentProgress_IsCompletedTrue_SetsProgress100() {
        // Arrange
        updateDto.setIsCompleted(true);
        updateDto.setProgress(20.0); // Should be overwritten to 100.0

        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.of(mockSlm));
        when(learningDocumentRepository.findById(anyLong())).thenReturn(Optional.of(mockDoc));
        when(staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentIdAndLearningDocument_DocumentId(anyLong(), anyLong()))
                .thenReturn(Optional.empty()); // Simulate new record

        // Act
        staffLearningDocumentService.updateDocumentProgress(updateDto);

        // Assert
        verify(staffLearningDocumentRepository).save(argThat(record ->
                record.getProgress() == 100.0 && record.getIsCompleted()
        ));
        // Verify cross-service call
        verify(staffLearningMaterialServiceImpl).updateMaterialProgress(500L, 10.0);
    }

    @Test
    @DisplayName("getProgress - Should throw exception if progress not found")
    void getProgress_NotFound_ThrowsException() {
        // Arrange
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.of(mockSlm));
        when(staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentIdAndLearningDocument_DocumentId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                staffLearningDocumentService.getProgress(UUID.randomUUID(), 1L, 100L)
        );
        assertEquals("No progress found for this document.", exception.getMessage());
    }

    @Test
    @DisplayName("getAllProgressByMaterial - Should return list of progress DTOs")
    void getAllProgressByMaterial_Success() {
        // Arrange
        StaffLearningDocumentProgress progress = new StaffLearningDocumentProgress();
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.of(mockSlm));
        when(staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentId(anyLong()))
                .thenReturn(List.of(progress));
        when(appMapper.toDto(any(StaffLearningDocumentProgress.class))).thenReturn(new StaffLearningDocumentProgressDto());

        // Act
        List<StaffLearningDocumentProgressDto> result = staffLearningDocumentService.getAllProgressByMaterial(UUID.randomUUID(), 1L);

        // Assert
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAllProgressByMaterial - Should throw exception if enrollment not found")
    void getAllProgressByMaterial_EnrollmentNotFound_ThrowsException() {
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                staffLearningDocumentService.getAllProgressByMaterial(UUID.randomUUID(), 1L));
    }

    @Test
    @DisplayName("updateDocumentProgress - Should throw exception if document not found")
    void updateDocumentProgress_DocumentNotFound_ThrowsException() {
        when(staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(any(), any()))
                .thenReturn(Optional.of(mockSlm));
        when(learningDocumentRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("learning.document.not.found"), any(), any())).thenReturn("Doc not found");

        assertThrows(BadRequestException.class, () -> staffLearningDocumentService.updateDocumentProgress(updateDto));
    }
}