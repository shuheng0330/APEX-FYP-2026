package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {
    @org.springframework.data.jpa.repository.Query("select s from Staff s join fetch s.role r left join fetch s.manager where s.isDeleted=false and s.accountStatus=:status and r.id in :roleIds and r.isDeleted=false and r.performanceReviewEligible=true")
    List<Staff> findEligibleReviewStaff(Set<Long> roleIds, com.tbm.careerpathlearning.enums.StaffAccountStatus status);

    Optional<Staff> findByIsDeletedIsFalseAndEmail(String email);

    List<Staff> findAllByIsDeletedIsFalse();

    List<Staff> findByRoleId(Long roleId);

    List<Staff> findAllByIsDeletedIsFalseAndManager_Id(UUID managerId);

    List<Staff> findAllByRoleIdIn(Set<Long> roleId);

    List<Staff> findAllByCareerPathway_Id(Long careerPathwayId);

    List<Staff> findAllByCareerPathway_IdIn(Set<Long> careerPathwayId);

    List<Staff> findAllByIsDeletedIsFalseAndIdIn(Set<UUID> staffIds);

    Optional<Staff> findByIdAndIsDeletedFalse(UUID userId);

}
