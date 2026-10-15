package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingAuditServiceImplTest {

    @Mock private TrainingAuditLogRepository trainingAuditLogRepository;
    @Mock private StaffRepository staffRepository;

    @InjectMocks
    private TrainingAuditServiceImpl service;

    private final Instant now = Instant.parse("2026-10-12T02:00:00Z");
    private final UUID admin = UUID.randomUUID();
    private final UUID trainer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ClockConfig.ZONE));
    }

    @Test
    @DisplayName("An action is saved with who, what, the reason and the time")
    void record() {
        service.record(admin, TrainingAuditAction.TRAINER_ACCESS_GRANTED, TrainingAuditTarget.STAFF, trainer.toString(),
                "Presents the product sharing session", "Until 31 Dec 2026");

        ArgumentCaptor<TrainingAuditLog> saved = ArgumentCaptor.forClass(TrainingAuditLog.class);
        verify(trainingAuditLogRepository).save(saved.capture());
        TrainingAuditLog entry = saved.getValue();
        assertEquals("TRAINER_ACCESS_GRANTED", entry.getAction());
        assertEquals("STAFF", entry.getTargetType());
        assertEquals(trainer.toString(), entry.getTargetId());
        assertEquals(admin, entry.getActorId());
        assertEquals("Presents the product sharing session", entry.getReason());
        assertEquals("Until 31 Dec 2026", entry.getDetail());
        assertEquals(now, entry.getCreatedAt().toInstant());
    }

    @Test
    @DisplayName("The history names who acted, shows scheduled jobs without a name, and keeps the newest-first order")
    void history() {
        Staff superAdmin = new Staff();
        superAdmin.setName("Super Admin");
        UUID removed = UUID.randomUUID();
        when(trainingAuditLogRepository.findAllByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc("STAFF", trainer.toString()))
                .thenReturn(List.of(entry(3L, null, "TRAINER_ACCESS_LAPSED"), entry(2L, removed, "TRAINER_ACCESS_END_DATE_CHANGED"),
                        entry(1L, admin, "TRAINER_ACCESS_GRANTED")));
        when(staffRepository.findById(admin)).thenReturn(Optional.of(superAdmin));
        when(staffRepository.findById(removed)).thenReturn(Optional.empty());

        List<TrainingAuditEntryDto> history = service.history(TrainingAuditTarget.STAFF, trainer.toString());

        assertEquals(List.of(3L, 2L, 1L), history.stream().map(TrainingAuditEntryDto::getId).toList());
        assertNull(history.get(0).getActorName(), "a scheduled job has no name");
        assertEquals("Unknown user", history.get(1).getActorName());
        assertEquals("Super Admin", history.get(2).getActorName());
        assertEquals("TRAINER_ACCESS_GRANTED", history.get(2).getAction());
    }

    private TrainingAuditLog entry(Long id, UUID actor, String action) {
        TrainingAuditLog e = new TrainingAuditLog();
        e.setId(id);
        e.setActorId(actor);
        e.setAction(action);
        e.setTargetType("STAFF");
        e.setTargetId(trainer.toString());
        e.setCreatedAt(OffsetDateTime.now(Clock.fixed(now, ClockConfig.ZONE)).minusDays(4 - id));
        return e;
    }
}
