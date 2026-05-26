package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffSelfDeclaredSkillDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffSelfDeclaredSkillService {

    StaffSelfDeclaredSkillDto findById(Long id);

    Optional<StaffSelfDeclaredSkillDto> getByStaffIdAndSkill(UUID staffId, String skill);

    List<StaffSelfDeclaredSkillDto> findByStaffId(UUID staffId);

    StaffSelfDeclaredSkillDto update(Long id, StaffSelfDeclaredSkillDto dto);

    StaffSelfDeclaredSkillDto create(StaffSelfDeclaredSkillDto dto);

    void delete(Long id);

    void deleteAllById(Set<Long> ids);

    void deleteAllByStaffId(UUID staffId);

    void deleteAllByStaffIdIn(Set<UUID> staffIds);
}
