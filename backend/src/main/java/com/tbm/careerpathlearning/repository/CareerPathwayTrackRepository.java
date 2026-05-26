package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CareerPathwayTrack;
import com.tbm.careerpathlearning.model.CareerPathwayTrackId;
import com.tbm.careerpathlearning.model.CompetencyCompTag;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CareerPathwayTrackRepository extends JpaRepository<CareerPathwayTrack, CareerPathwayTrackId> {

    List<CareerPathwayTrack> findAllByCareerPathway_Id(Long careerPathwayId);

    List<CareerPathwayTrack> findAllByCareerPathway_IdIn(Set<Long> careerPathwayId);

    List<CareerPathwayTrack> findAllByTrackIdIn(Set<Long> trackIds);
}
