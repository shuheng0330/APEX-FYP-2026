package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrackDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Track;
import com.tbm.careerpathlearning.repository.TrackRepository;
import com.tbm.careerpathlearning.service.TrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrackServiceImpl implements TrackService {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private MessageSource messageSource;

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UPDATE_OPERATION = "Update Career Pathway Tag";

    @Override
    public List<TrackDto> findAllByIsDeletedIsFalse() {
        return trackRepository.findAllByIsDeletedIsFalse().stream().map(appMapper::toDto).collect(Collectors.toList());

    }

    @Override
    public List<TrackDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids) {
        return trackRepository.findAllByIsDeletedIsFalseAndIdIn(ids).stream()
                .map(appMapper::toDto).collect(Collectors.toList());

    }

    @Override
    public List<TrackDto> findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(Set<String> tracks) {
        return trackRepository.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(tracks).stream()
                .map(appMapper::toDto).toList();
    }

    @Transactional
    @Override
    public List<TrackDto> createAll(List<TrackDto> dtos) {
        // normalize and deduplicate incoming list
        Map<String, TrackDto> normalized = dtos.stream()
                .peek(dto-> {
                    if (dto.getTrack() == null ||
                            dto.getTrack().trim().length() > 100) {
                        String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                                new String[]{UPDATE_OPERATION}, Locale.getDefault());

                        throw new BadRequestException(errorMessage);
                    }
                })
                .collect(Collectors.toMap(
                        dto -> dto.getTrack().trim().toLowerCase(),
                        dto -> dto,
                        (first, second) -> first // keep first if duplicate
                ));

        Set<String> tracks = normalized.keySet();

        List<TrackDto> existingTrackDto = this.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(tracks);

        Set<String> existingTracks = existingTrackDto.stream()
                .map(tag -> tag.getTrack().trim().toLowerCase())
                .collect(Collectors.toSet());

        List<TrackDto> toCreate = normalized.entrySet().stream()
                .filter(entry -> !existingTracks.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        if (toCreate.isEmpty()) {
            return existingTrackDto; // everything already existed
        }

        List<Track> entities = toCreate.stream()
                .map(appMapper::toEntity)
                .toList();

        List<TrackDto> created = trackRepository.saveAll(entities).stream()
                .map(appMapper::toDto)
                .toList();

        List<TrackDto> result = new ArrayList<>(existingTrackDto);
        result.addAll(created);
        return result;
    }

    @Transactional
    @Override
    public void deleteAllByIdIn(Set<Long> ids, UUID userId, OffsetDateTime now) {
        List<Track> entities = trackRepository.findAllByIsDeletedIsFalseAndIdIn(ids).stream().peek(entity ->
        {
            entity.setDeleted(true);
            entity.setUpdatedBy(userId);
            entity.setUpdatedAt(now);
        }).toList();

        trackRepository.saveAll(entities);
    }
}


