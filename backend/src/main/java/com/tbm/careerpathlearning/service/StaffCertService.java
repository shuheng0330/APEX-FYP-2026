package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffCertDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffCertService {

    StaffCertDto findById(Long id);

    Optional<StaffCertDto> findByStaff_IdAndFileNameIgnoreCase(UUID staffId, String fileName);

    List<StaffCertDto> findByStaffId(UUID staffId);

    StaffCertDto create(StaffCertDto dto);

    StaffCertDto update(StaffCertDto dto);

    StaffCertDto findAndDeleteById(Long id);

    List<StaffCertDto> findAndDeleteByIdIn(Set<Long> ids);

    void deleteAllByStaffId(UUID staffId);

    void deleteAllByStaffIdIn(Set<UUID> ids);
}
