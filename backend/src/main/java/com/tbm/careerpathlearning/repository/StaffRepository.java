package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Optional<Staff> findByIsDeletedIsFalseAndEmail(String email);

    List<Staff> findAllByIsDeletedIsFalse();

    List<Staff> findByRoleId(Long roleId);

    List<Staff> findAllByIsDeletedIsFalseAndManager_Id(UUID managerId);

    List<Staff> findAllByRoleIdIn(Set<Long> roleId);

    List<Staff> findAllByCareerPathway_Id(Long careerPathwayId);

    List<Staff> findAllByCareerPathway_IdIn(Set<Long> careerPathwayId);

    List<Staff> findAllByIsDeletedIsFalseAndIdIn(Set<UUID> staffIds);

    Optional<Staff> findByIdAndIsDeletedFalse(UUID userId);

    /** Active staff with their role and department loaded, for training audiences (FR-09-02). */
    @Query("select s from Staff s left join fetch s.role r left join fetch r.orgChart "
            + "where s.isDeleted = false and s.accountStatus = com.tbm.careerpathlearning.enums.StaffAccountStatus.ACTIVE")
    List<Staff> findAllActiveWithRole();

    /** Staff with their role and department loaded, whatever their account status (FR-10-01). */
    @Query("select s from Staff s left join fetch s.role r left join fetch r.orgChart where s.id in :ids")
    List<Staff> findAllWithRoleByIdIn(@Param("ids") Collection<UUID> ids);

}
