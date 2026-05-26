package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrackDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Track;
import com.tbm.careerpathlearning.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackServiceImplTest {

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private TrackServiceImpl trackService;

    private Track mockEntity;
    private TrackDto mockDto;
    private final UUID USER_ID = UUID.randomUUID();
    private final OffsetDateTime NOW = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        mockEntity = new Track();
        mockEntity.setId(1L);
        mockEntity.setTrack("Technical");
        mockEntity.setDeleted(false);

        mockDto = new TrackDto();
        mockDto.setId(1L);
        mockDto.setTrack("Technical");
        mockDto.setDeleted(false);
        mockDto.setCreatedBy(USER_ID);
        mockDto.setUpdatedAt(NOW);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error");
    }

    // --- Find Tests ---

    @Test
    void findAllByIsDeletedIsFalse_ShouldReturnList() {
        when(trackRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<TrackDto> result = trackService.findAllByIsDeletedIsFalse();
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndIdIn_ShouldReturnList() {
        Set<Long> ids = Set.of(1L);
        when(trackRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<TrackDto> result = trackService.findAllByIsDeletedIsFalseAndIdIn(ids);
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn_ShouldReturnList() {
        Set<String> tracks = Set.of("Technical");
        when(trackRepository.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(anySet())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<TrackDto> result = trackService.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(tracks);
        assertEquals(1, result.size());
    }

    // --- CreateAll Tests (Deduplication Logic) ---

    @Test
    void createAll_ShouldCreateOnlyNewTracks() {
        // Scenario: Input has "Technical" (Exists) and "Management" (New).
        // Expected: Only "Management" is saved. Result contains both.

        TrackDto techDto = new TrackDto(); techDto.setTrack("Technical");
        TrackDto mgmtDto = new TrackDto(); mgmtDto.setTrack("Management");
        List<TrackDto> input = List.of(techDto, mgmtDto);

        // Mock DB finding "Technical"
        when(trackRepository.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(anySet())).thenReturn(List.of(mockEntity)); // Entity is "Technical"
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto); // DTO is "Technical"

        // Mock saving "Management"
        Track mgmtEntity = new Track(); mgmtEntity.setId(2L); mgmtEntity.setTrack("Management");
        when(appMapper.toEntity(mgmtDto)).thenReturn(mgmtEntity);
        when(trackRepository.saveAll(anyList())).thenReturn(List.of(mgmtEntity));

        TrackDto savedMgmtDto = new TrackDto(); savedMgmtDto.setId(2L); savedMgmtDto.setTrack("Management");
        when(appMapper.toDto(mgmtEntity)).thenReturn(savedMgmtDto);

        List<TrackDto> result = trackService.createAll(input);

        assertEquals(2, result.size()); // Should contain Technical (existing) + Management (created)
        verify(trackRepository).saveAll(argThat(list -> ((List<?>)list).size() == 1)); // Only 1 saved
    }

    @Test
    void createAll_ShouldReturnExisting_WhenAllTracksExist() {
        // Scenario: Input "Technical". DB "Technical".
        TrackDto techDto = new TrackDto(); techDto.setTrack("Technical");

        when(trackRepository.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(anySet())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        List<TrackDto> result = trackService.createAll(List.of(techDto));

        assertEquals(1, result.size());
        verify(trackRepository, never()).saveAll(anyList()); // Nothing new to save
    }

    @Test
    void createAll_ShouldThrowException_WhenTrackIsNull() {
        // Scenario: Input track name is null
        TrackDto invalidDto = new TrackDto(); invalidDto.setTrack(null);
        List<TrackDto> input = List.of(invalidDto);

        assertThrows(BadRequestException.class, () -> trackService.createAll(input));
    }

    @Test
    void createAll_ShouldThrowException_WhenTrackTooLong() {
        // Scenario: Input track name > 100 chars
        String longName = "a".repeat(101);
        TrackDto invalidDto = new TrackDto(); invalidDto.setTrack(longName);
        List<TrackDto> input = List.of(invalidDto);

        assertThrows(BadRequestException.class, () -> trackService.createAll(input));
    }

    @Test
    void createAll_ShouldHandleDuplicateInputs_ByProcessingOnce() {
        // Scenario: Input list has ["Technical", "Technical"].
        // Expected: Processed as 1 item.
        TrackDto t1 = new TrackDto(); t1.setTrack("Technical");
        TrackDto t2 = new TrackDto(); t2.setTrack("Technical");

        // Mock DB returns empty (so it tries to create)
        when(trackRepository.findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(anySet())).thenReturn(Collections.emptyList());

        when(appMapper.toEntity(any(TrackDto.class))).thenReturn(mockEntity);
        when(trackRepository.saveAll(anyList())).thenReturn(List.of(mockEntity));
        when(appMapper.toDto(mockEntity)).thenReturn(mockDto);

        trackService.createAll(List.of(t1, t2));

        // Verify saveAll received a list of size 1, not 2
        verify(trackRepository).saveAll(argThat(list -> ((List<?>)list).size() == 1));
    }

    // --- Delete Tests ---

    @Test
    void deleteAllByIdIn_ShouldSoftDelete() {
        Set<Long> ids = Set.of(1L);
        when(trackRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockEntity));

        trackService.deleteAllByIdIn(ids, USER_ID, NOW);

        verify(trackRepository).saveAll(argThat(list -> {
            List<Track> entities = (List<Track>) list;
            return !entities.isEmpty() && entities.get(0).getDeleted();
        }));
    }
}