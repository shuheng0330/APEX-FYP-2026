package com.tbm.careerpathlearning.service;


import com.tbm.careerpathlearning.dto.ParentChildNodeDto;
import com.tbm.careerpathlearning.enums.RelationType;
import jakarta.mail.search.SearchTerm;

import java.util.List;
import java.util.Set;

public interface ParentChildNodeService {

    List<ParentChildNodeDto> findAllByRelationType(RelationType relationType);

    void deleteAllByParentIdInOrChildIdIn(Set<Long> nodeIds);

    List<ParentChildNodeDto> createALl(List<ParentChildNodeDto> dtos, RelationType relationType);
}
