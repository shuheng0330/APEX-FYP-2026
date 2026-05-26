package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrackDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TrackService {

    List<TrackDto> findAllByIsDeletedIsFalse();

    List<TrackDto> findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(Set<String> tracks);

    List<TrackDto> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids);

    List<TrackDto> createAll(List<TrackDto> dtos);

    void deleteAllByIdIn(Set<Long> ids, UUID userId, OffsetDateTime now);
}
