package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.ParentChildNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ParentChildNodeRepository extends JpaRepository<ParentChildNode, Long> {

    List<ParentChildNode> findAllByRelationType(RelationType relationType);

    void deleteAllByParentIdInOrChildIdIn(Set<Long> parentIds, Set<Long> childIds);

}