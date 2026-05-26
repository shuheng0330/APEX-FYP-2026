package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ParentChildNodeDto;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.ParentChildNode;
import com.tbm.careerpathlearning.repository.ParentChildNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParentChildNodeServiceImplTest {

    @Mock
    private AppMapper appMapper;

    @Mock
    private ParentChildNodeRepository parentChildNodeRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ParentChildNodeServiceImpl parentChildNodeService;

    private ParentChildNode mockEntity;
    private ParentChildNodeDto mockDto;
    private final RelationType REL_TYPE = RelationType.ORG_CHART;
    private final UUID USER_ID = UUID.randomUUID();
    private final OffsetDateTime NOW = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        mockEntity = new ParentChildNode();
        mockEntity.setParentId(1L);
        mockEntity.setChildId(2L);
        mockEntity.setRelationType(REL_TYPE);

        mockDto = new ParentChildNodeDto();
        mockDto.setParentId(1L);
        mockDto.setChildId(2L);
        mockDto.setRelationType(REL_TYPE);
        // Set audit fields to pass validation
        mockDto.setCreatedBy(USER_ID);
        mockDto.setUpdatedBy(USER_ID);
        mockDto.setCreatedAt(NOW);
        mockDto.setUpdatedAt(NOW);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void findAllByRelationType_ShouldReturnList() {
        when(parentChildNodeRepository.findAllByRelationType(REL_TYPE)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<ParentChildNodeDto> result = parentChildNodeService.findAllByRelationType(REL_TYPE);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getParentId());
    }

    // --- Delete Tests ---

    @Test
    void deleteAllByParentIdInOrChildIdIn_ShouldCallRepo() {
        Set<Long> ids = Set.of(1L, 2L);
        parentChildNodeService.deleteAllByParentIdInOrChildIdIn(ids);
        verify(parentChildNodeRepository).deleteAllByParentIdInOrChildIdIn(ids, ids);
    }

    // --- CreateAll Tests (Synchronization Logic) ---

    @Test
    void createAll_ShouldThrowException_WhenListIsEmpty() {
        List<ParentChildNodeDto> emptyList = Collections.emptyList();
        assertThrows(BadRequestException.class, () -> parentChildNodeService.createALl(emptyList, REL_TYPE));
    }

    @Test
    void createAll_ShouldSync_AddRemoveAndRetain() {
        // --- Scenario ---
        // Existing in DB:  A(1->2), B(3->4)
        // Incoming Input:  B(3->4), C(5->6)
        // Expected Result: Delete A, Keep B, Create C. Result list has B and C.

        // 1. Setup Existing (A and B)
        ParentChildNode entityA = createEntity(1L, 2L);
        ParentChildNode entityB = createEntity(3L, 4L);
        ParentChildNodeDto dtoA = createDto(1L, 2L);
        ParentChildNodeDto dtoB = createDto(3L, 4L);

        // Mock DB returning A and B
        when(parentChildNodeRepository.findAllByRelationType(REL_TYPE)).thenReturn(List.of(entityA, entityB));
        when(appMapper.toDto(entityA)).thenReturn(dtoA);
        when(appMapper.toDto(entityB)).thenReturn(dtoB);

        // 2. Setup Incoming (B and C)
        ParentChildNodeDto incomingB = createDto(3L, 4L);
        ParentChildNodeDto incomingC = createDto(5L, 6L); // New
        List<ParentChildNodeDto> inputList = List.of(incomingB, incomingC);

        // 3. Mock Saving New Entity (C)
        ParentChildNode entityC = createEntity(5L, 6L);
        when(appMapper.toEntity(incomingC)).thenReturn(entityC);
        when(parentChildNodeRepository.saveAll(anyList())).thenReturn(List.of(entityC));
        when(appMapper.toDto(entityC)).thenReturn(incomingC);

        // 4. Mock Deleting Old Entity (A)
        // The service converts the existing DTO back to Entity to delete it
        when(appMapper.toEntity(dtoA)).thenReturn(entityA);

        // --- Act ---
        List<ParentChildNodeDto> result = parentChildNodeService.createALl(inputList, REL_TYPE);

        // --- Assert ---
        assertEquals(2, result.size()); // Should contain B and C

        // Verify Delete (A was removed)
        verify(parentChildNodeRepository).deleteAll(argThat(iterable -> {
            List<ParentChildNode> list = (List<ParentChildNode>) iterable; // Cast to List
            return list.size() == 1 && list.get(0).getParentId() == 1L && list.get(0).getChildId() == 2L;
        }));

        // Verify Save (C was added)
        verify(parentChildNodeRepository).saveAll(argThat(iterable -> {
            List<ParentChildNode> list = (List<ParentChildNode>) iterable; // Cast to List
            return list.size() == 1 && list.get(0).getParentId() == 5L;
        }));
    }

    // --- Validation Logic inside CreateAll ---

    @Test
    void createAll_ShouldThrowException_WhenSelfReference() {
        // Parent = Child
        ParentChildNodeDto invalidDto = createDto(1L, 1L);
        List<ParentChildNodeDto> input = List.of(invalidDto);

        // Mock empty DB
        when(parentChildNodeRepository.findAllByRelationType(REL_TYPE)).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> parentChildNodeService.createALl(input, REL_TYPE));
    }

    @Test
    void createAll_ShouldThrowException_WhenRelationTypeMismatch() {
        ParentChildNodeDto invalidDto = createDto(1L, 2L);
        invalidDto.setRelationType(RelationType.CAREER_PATHWAY); // Mismatch
        List<ParentChildNodeDto> input = List.of(invalidDto);

        when(parentChildNodeRepository.findAllByRelationType(REL_TYPE)).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> parentChildNodeService.createALl(input, REL_TYPE));
    }

    @Test
    void createAll_ShouldThrowException_WhenAuditFieldsMissing() {
        ParentChildNodeDto invalidDto = createDto(1L, 2L);
        invalidDto.setCreatedBy(null); // Missing Audit
        List<ParentChildNodeDto> input = List.of(invalidDto);

        when(parentChildNodeRepository.findAllByRelationType(REL_TYPE)).thenReturn(Collections.emptyList());

        assertThrows(BadRequestException.class, () -> parentChildNodeService.createALl(input, REL_TYPE));
    }

    // --- Helpers ---

    private ParentChildNodeDto createDto(Long p, Long c) {
        ParentChildNodeDto dto = new ParentChildNodeDto();
        dto.setParentId(p);
        dto.setChildId(c);
        dto.setRelationType(REL_TYPE);
        dto.setCreatedBy(USER_ID);
        dto.setUpdatedBy(USER_ID);
        dto.setCreatedAt(NOW);
        dto.setUpdatedAt(NOW);
        return dto;
    }

    private ParentChildNode createEntity(Long p, Long c) {
        ParentChildNode entity = new ParentChildNode();
        entity.setParentId(p);
        entity.setChildId(c);
        entity.setRelationType(REL_TYPE);
        return entity;
    }
}