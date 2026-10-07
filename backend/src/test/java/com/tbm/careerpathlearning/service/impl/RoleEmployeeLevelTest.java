package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.ReviewFrequency;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RoleEmployeeLevelTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final EmployeeLevelRepository levels = mock(EmployeeLevelRepository.class);
    private final RoleServiceImpl service = new RoleServiceImpl();
    private Role existing;
    private RoleDto request;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "roleRepository", roles);
        ReflectionTestUtils.setField(service, "employeeLevelRepository", levels);
        ReflectionTestUtils.setField(service, "appMapper", Mappers.getMapper(AppMapper.class));
        ReflectionTestUtils.setField(service, "messageSource", mock(MessageSource.class));
        ReflectionTestUtils.setField(service, "validationService", mock(ValidationService.class));
        var department = new OrgChart(); department.setId(100L); department.setName("Sales");
        existing = new Role(); existing.setId(10L); existing.setName("Role"); existing.setOrgChart(department);
        existing.setEmployeeLevel(EmployeeLevelFixtures.levels().get(3));
        existing.setDefaultReviewFrequency(ReviewFrequency.QUARTERLY);
        when(roles.findById(10L)).thenReturn(Optional.of(existing));
        when(levels.findById(4L)).thenReturn(Optional.of(existing.getEmployeeLevel()));
        when(roles.save(any())).thenAnswer(i -> i.getArgument(0));
        when(roles.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        request = new RoleDto(); request.setName("Role");
        var dto = new OrgChartDto(); dto.setId(100L); dto.setName("Sales"); request.setOrgChart(dto);
    }

    @Test
    void creationRequiresKnownLevelAndReturnsLevelMetadata() {
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BadRequestException.class).hasMessageContaining("Select");
        request.setEmployeeLevelId(999L);
        assertThatThrownBy(() -> service.create(request)).hasMessageContaining("does not exist");
        request.setEmployeeLevelId(4L);
        var created = service.create(request);
        assertThat(created.getEmployeeLevelId()).isEqualTo(4L);
        assertThat(created.getEmployeeLevelCode()).isEqualTo("EXECUTIVE");
    }

    @Test
    void omittedLevelOnMappedRolePreservesLevelAndFrequency() {
        var updated = service.update(10L, request);
        assertThat(updated.getEmployeeLevelId()).isEqualTo(4L);
        verify(roles).save(argThat(r -> r.getDefaultReviewFrequency() == ReviewFrequency.QUARTERLY));
    }

    @Test
    void inheritedRoleNeedsClassificationWhenEdited() {
        existing.setEmployeeLevel(null);
        assertThatThrownBy(() -> service.update(10L, request)).hasMessageContaining("Select");
        request.setEmployeeLevelId(4L);
        when(levels.findById(4L)).thenReturn(Optional.of(EmployeeLevelFixtures.levels().get(3)));
        assertThat(service.update(10L, request).getEmployeeLevelId()).isEqualTo(4L);
    }

    @Test
    void explicitReclassificationDoesNotEraseDefaultFrequency() {
        when(levels.findById(6L)).thenReturn(Optional.of(EmployeeLevelFixtures.levels().get(5)));
        request.setEmployeeLevelId(6L);
        assertThat(service.update(10L, request).getEmployeeLevelCode()).isEqualTo("GENERAL");
        verify(roles).save(argThat(r -> r.getDefaultReviewFrequency() == ReviewFrequency.QUARTERLY));
    }

    @Test
    void legacyImportPreservesMappingAndFrequencyButCannotCreateUnclassifiedRole() {
        request.setId(10L);
        when(roles.findAllByIsDeletedIsFalse()).thenReturn(List.of(existing));
        assertThat(service.createAndUpdateAll(List.of(request)).get(0).getEmployeeLevelId()).isEqualTo(4L);
        verify(roles).saveAll(argThat(rows -> ((List<Role>) rows).get(0).getDefaultReviewFrequency() == ReviewFrequency.QUARTERLY));
        request.setId(null); request.setName("New Role");
        assertThatThrownBy(() -> service.createAndUpdateAll(List.of(request))).hasMessageContaining("Select");
    }

    @Test
    void bulkUpdatePreservesMappingAndDefaultFrequency() {
        request.setId(10L);
        when(roles.findAllById(any())).thenReturn(List.of(existing));
        when(roles.findAllByIdInAndIsDeletedIsFalse(anySet())).thenReturn(List.of(existing));
        assertThat(service.updateAll(Set.of(10L), List.of(request)).get(0).getEmployeeLevelId()).isEqualTo(4L);
        verify(roles).saveAll(argThat(rows -> ((List<Role>) rows).get(0).getDefaultReviewFrequency() == ReviewFrequency.QUARTERLY));
    }

    @Test
    void softDeletionPreservesConfigurationWithoutRequiringClassification() {
        service.delete(10L, UUID.randomUUID());
        verify(roles).save(argThat(r -> r.isDeleted() && r.getEmployeeLevel().getId().equals(4L)
                && r.getDefaultReviewFrequency() == ReviewFrequency.QUARTERLY));
    }
}
