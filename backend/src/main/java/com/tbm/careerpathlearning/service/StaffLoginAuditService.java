package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffLoginAuditDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffLoginAuditService {
    List<StaffLoginAuditDto> getAllStaffLoginAudits();

    StaffLoginAuditDto getStaffLoginAuditById(UUID id);

    Optional<StaffLoginAuditDto> findStaffLoginAuditById(UUID id);

    List<StaffLoginAuditDto> getStaffLoginAuditsByIdIn(Set<UUID> ids);

    StaffLoginAuditDto updateStaffLoginAudit(UUID id, StaffLoginAuditDto dto);

    StaffLoginAuditDto createStaffLoginAudit(StaffLoginAuditDto dto);

    void deleteStaffLoginAudit(UUID staffID);

    void deleteAllByStaffIdIn(Set<UUID> staffIds);

}
