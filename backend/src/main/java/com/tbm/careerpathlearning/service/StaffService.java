package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CareerPathwayDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.dto.StaffDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffService {
    List<StaffDto> findAll();

    List<StaffDto> findAllByIsDeletedIsFalse();

    StaffDto findById(UUID id);

    List<StaffDto> findAllByIdIn(Set<UUID> ids);

    List<StaffDto> findAllByIsDeletedIsFalseAndIdIn(Set<UUID> ids);

    Optional<StaffDto> findByIsDeletedIsFalseAndEmail(String email);

    List<StaffDto> findAllByIsDeletedIsFalseAndManagerId(UUID managerId);

    List<StaffDto> findAllByRoleId(Long roleId);

    List<StaffDto> findAllByRoleIdIn(Set<Long> roleIds);

    List<StaffDto> findAllByCareerPathwayId(Long careerPathwayId);

    List<StaffDto> findAllByCareerPathwayIdIn(Set<Long> careerPathwayIds);

    StaffDto update(UUID id, StaffDto dto);

    List<StaffDto> updateAll(List<StaffDto> dtos);

    List<StaffDto> updateRoleByStaffIdIn(Set<UUID> staffIds, RoleDto roleDto, UUID userId);

    List<StaffDto> updateCareerPathwayByIdIn(Set<UUID> staffIds, CareerPathwayDto careerPathwayDto, UUID userId, OffsetDateTime now);

    StaffDto create(StaffDto dto);

    void delete(UUID staffID, UUID userId);

    void deleteAllById(Set<UUID> staffIds, UUID userId);

    Set<UUID> getAllDownlineStaffIds(UUID userUUID);

}
