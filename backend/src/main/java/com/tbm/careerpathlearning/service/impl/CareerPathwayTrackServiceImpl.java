package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.CareerPathwayTrack;
import com.tbm.careerpathlearning.model.CareerPathwayTrackId;
import com.tbm.careerpathlearning.repository.CareerPathwayTrackRepository;
import com.tbm.careerpathlearning.service.CareerPathwayService;
import com.tbm.careerpathlearning.service.CareerPathwayTrackService;
import com.tbm.careerpathlearning.service.TrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CareerPathwayTrackServiceImpl implements CareerPathwayTrackService {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private CareerPathwayService careerPathwayService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private CareerPathwayTrackRepository careerPathwayTrackRepository;

    @Autowired
    private AppMapper appMapper;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String UNIQUE_ATTRIBUTE_ERR_TITLE_CODE = "attribute.unique.err.title";

    private static final String UNIQUE_ATTRIBUTE_ERR_MSG_CODE = "attribute.unique.err.msg";

    private static final String ID_ATTRIBUTE = "ID";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String ASSIGN_TRACK = "Assigning tracks to the career pathway";

    @Override
    public List<CareerPathwayTrackDto> findAll() {
        return careerPathwayTrackRepository.findAll().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayTrackDto> findAllByIdIn(Set<CareerPathwayTrackId> ids) {
        return careerPathwayTrackRepository.findAllById(ids).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayTrackDto> findAllByCareerPathwayId(Long careerPathwayId) {
        return careerPathwayTrackRepository.findAllByCareerPathway_Id(careerPathwayId).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<CareerPathwayTrackDto> findAllByTrackIdIn(Set<Long> trackIds) {
        return careerPathwayTrackRepository.findAllByTrackIdIn(trackIds).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<CareerPathwayTrackDto> createAll(List<CareerPathwayTrackDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{ASSIGN_TRACK}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> careerPathwayIds = dtos.stream()
                .map(dto -> dto.getId().getCareerPathwayId()).collect(Collectors.toSet());

        Set<Long> trackIds = dtos.stream()
                .map(dto -> dto.getId().getTrackId()).collect(Collectors.toSet());

        Set<CareerPathwayTrackId> careerPathwayTrackIds = dtos.stream()
                .map(CareerPathwayTrackDto::getId).collect(Collectors.toSet());

        Set<Long> existingCareerPathwayIds = careerPathwayService.getAllByIsDeletedIsFalseAndIdIn(careerPathwayIds).stream()
                .map(CareerPathwayDto::getId).collect(Collectors.toSet());

        Set<Long> existingTrackIds = trackService.findAllByIsDeletedIsFalseAndIdIn(trackIds)
                .stream().map(TrackDto::getId).collect(Collectors.toSet());

        Set<CareerPathwayTrackId> existingCareerPathwayTrackIds = this.findAllByIdIn(careerPathwayTrackIds).stream()
                .map(CareerPathwayTrackDto::getId).collect(Collectors.toSet());

        List<CareerPathwayTrack> entities = dtos.stream().map(dto -> {
                    Long careerPathwayId = dto.getId().getCareerPathwayId();
                    Long trackId = dto.getId().getTrackId();

                    if (!existingCareerPathwayIds.contains(careerPathwayId)) {
                        String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                        throw new DataAccessException(errorMessage);
                    }

                    if (!existingTrackIds.contains(trackId)) {
                        String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
                        throw new DataAccessException(errorMessage);
                    }

                    if (existingCareerPathwayTrackIds.contains(dto.getId())) {
                        String errorTitle = messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_TITLE_CODE, new String[]{ID_ATTRIBUTE}, Locale.getDefault());
                        String errorMessage = messageSource.getMessage(UNIQUE_ATTRIBUTE_ERR_MSG_CODE,
                                new String[]{ID_ATTRIBUTE, ASSIGN_TRACK},
                                Locale.getDefault());

                        throw new BadRequestException(errorTitle, errorMessage);
                    }

                    return appMapper.toEntity(dto);
                }
        ).toList();

        return careerPathwayTrackRepository.saveAll(entities).stream()
                .map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void deleteAllById(Set<CareerPathwayTrackId> ids) {
        careerPathwayTrackRepository.deleteAllById(ids);
    }

    @Transactional
    @Override
    public List<CareerPathwayTrackDto> findAndDeleteByCareerPathwayId(Long careerPathwayId) {
        List<CareerPathwayTrack> toDelete = careerPathwayTrackRepository.findAllByCareerPathway_Id(careerPathwayId);
        careerPathwayTrackRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<CareerPathwayTrackDto> findAndDeleteByCareerPathwayIdIn(Set<Long> careerPathwayIds) {
        List<CareerPathwayTrack> toDelete = careerPathwayTrackRepository.findAllByCareerPathway_IdIn(careerPathwayIds);
        careerPathwayTrackRepository.deleteAll(toDelete);

        return toDelete.stream().map(appMapper::toDto).collect(Collectors.toList());
    }

}
