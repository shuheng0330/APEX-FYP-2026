package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.StaffSelfDeclaredSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffSelfDeclaredSkillRepository extends JpaRepository<StaffSelfDeclaredSkill, Long> {

    Optional<StaffSelfDeclaredSkill> findByStaff_IdAndSkillIgnoreCase(UUID staffId, String skill);

    List<StaffSelfDeclaredSkill> findAllByStaff_Id(UUID staffId);

    void deleteAllByStaff_Id(UUID staffId);

    void deleteAllByStaff_IdIn(Set<UUID> staffIds);
}
