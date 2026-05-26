package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CompetencyCompTag;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface CompetencyCompTagRepository extends JpaRepository<CompetencyCompTag, CompetencyCompTagId> {

    List<CompetencyCompTag> findAllByCompetency_Id(Long competencyId);

    List<CompetencyCompTag> findAllByCompetency_IdIn(Set<Long> competencyIds);

    List<CompetencyCompTag> findAllByCompTag_Id(Long compTagId);

    List<CompetencyCompTag> findAllByCompTag_IdIn(Set<Long> competencyIds);

    List<CompetencyCompTag> findAllByCompTag_IdInAndCompetency_IdNot(Set<Long> compTagIds, Long competencyId);

    List<CompetencyCompTag> findAllByCompTag_IdInAndCompetency_IdNotIn(Set<Long> compTag_id, Set<Long> competency_id);

    void deleteAllByCompetency_IdAndCompTag_IdIn(Long competency_id, Collection<Long> compTag_id);
}
