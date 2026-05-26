package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CareerPathwayTrackDto;
import com.tbm.careerpathlearning.dto.CompetencyCompTagDto;
import com.tbm.careerpathlearning.model.CareerPathwayTrackId;
import com.tbm.careerpathlearning.model.CompetencyCompTagId;

import java.util.List;
import java.util.Set;

public interface CareerPathwayTrackService {

    List<CareerPathwayTrackDto> findAll();

    List<CareerPathwayTrackDto> findAllByIdIn(Set<CareerPathwayTrackId> ids);

    List<CareerPathwayTrackDto> findAllByCareerPathwayId(Long careerPathwayId);

    List<CareerPathwayTrackDto> findAllByTrackIdIn(Set<Long> trackIds);

    List<CareerPathwayTrackDto> createAll(List<CareerPathwayTrackDto> dtos);

    void deleteAllById(Set<CareerPathwayTrackId> ids);

    List<CareerPathwayTrackDto> findAndDeleteByCareerPathwayId(Long careerPathwayId);

    List<CareerPathwayTrackDto> findAndDeleteByCareerPathwayIdIn(Set<Long> careerPathwayIds);
}
