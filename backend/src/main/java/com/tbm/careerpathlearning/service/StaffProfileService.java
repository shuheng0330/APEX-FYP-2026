package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffProfileDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffProfileService {

    List<StaffProfileDto> findAll();

    StaffProfileDto findById(UUID id);

    Optional<StaffProfileDto> getById(UUID userId);

    StaffProfileDto update(UUID staffId, StaffProfileDto dto);

    StaffProfileDto updateProfilePicture(UUID staffId, StaffProfileDto dto);

    StaffProfileDto create(StaffProfileDto dto);

    List<StaffProfileDto> createAll(List<StaffProfileDto> dtos);

    void delete(UUID staffID);

    void deleteAllById(Set<UUID> staffIds);
}
