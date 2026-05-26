package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CareerPathwayRole;
import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CareerPathwayRoleRepository extends JpaRepository<CareerPathwayRole, CareerPathwayRoleId> {

    List<CareerPathwayRole> findAllByChildRoleId(Long roleId);

    List<CareerPathwayRole> findAllByParentRoleId(Long roleId);

    List<CareerPathwayRole> findByCareerPathwayId(Long careerPathwayId);

    List<CareerPathwayRole> findAllByIdIn(Set<CareerPathwayRoleId> ids);

    void deleteAllByCareerPathwayId(Long careerPathwayId);

    void deleteAllByCareerPathway_IdIn(Set<Long> careerPathwayIds);
}
