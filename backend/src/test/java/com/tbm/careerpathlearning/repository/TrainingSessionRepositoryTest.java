package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rebuilt session tables against a real database (H2): sessions keep their audience and settings, the calendar
 * query works, and unanswered invitations become No Response once their session starts (FR-12-14).
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TrainingSessionRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Autowired private TestEntityManager entityManager;
    @Autowired private TrainingProgramRepository trainingProgramRepository;
    @Autowired private TrainingRegistrationRepository trainingRegistrationRepository;
    @Autowired private TrainingInvitationRepository trainingInvitationRepository;
    @Autowired private TrainingAttendanceRepository trainingAttendanceRepository;

    private TrainingProgram session(String title, LocalDate date, LocalTime start) {
        TrainingProgram s = new TrainingProgram();
        s.setTitle(title);
        s.setSessionDate(date);
        s.setStartTime(start);
        s.setEndTime(start.plusHours(2));
        s.setVenue("Conference Hall B");
        s.setCapacity(20);
        s.setTrainingType(TrainingType.PRODUCT);
        s.setCreatedAt(LocalDateTime.of(2026, 10, 12, 10, 0));
        return trainingProgramRepository.save(s);
    }

    private TrainingRegistration registration(TrainingProgram session, TrainingRegistrationStatus status) {
        TrainingRegistration r = new TrainingRegistration();
        r.setTrainingId(session.getTrainingId());
        r.setStaffId(UUID.randomUUID());
        r.setStatus(status);
        return trainingRegistrationRepository.save(r);
    }

    private TrainingInvitation invitation(TrainingProgram session, TrainingInvitationStatus status) {
        TrainingInvitation i = new TrainingInvitation();
        i.setTrainingId(session.getTrainingId());
        i.setStaffId(UUID.randomUUID());
        i.setInvitedBy(UUID.randomUUID());
        i.setStatus(status);
        return trainingInvitationRepository.save(i);
    }

    @Test
    @DisplayName("A session keeps its audience, settings and status through a save and reload")
    void roundTrip() {
        UUID named = UUID.randomUUID();
        TrainingProgram s = session("Launch", DAY, LocalTime.of(9, 0));
        s.getDepartmentIds().addAll(Set.of(10L, 11L));
        s.getRoleIds().add(21L);
        s.getStaffIds().add(named);
        s.setRegistrationCutoff(DAY.minusDays(2));
        s.setDescription("x".repeat(3000));
        entityManager.flush();
        entityManager.clear();

        TrainingProgram loaded = trainingProgramRepository.findByTrainingIdAndDeletedFalse(s.getTrainingId()).orElseThrow();

        assertEquals(Set.of(10L, 11L), loaded.getDepartmentIds());
        assertEquals(Set.of(21L), loaded.getRoleIds());
        assertEquals(Set.of(named), loaded.getStaffIds());
        assertEquals(TrainingSessionStatus.SCHEDULED, loaded.getStatus());
        assertEquals(TrainingSessionKind.TRAINING, loaded.getKind());
        assertEquals(TrainingDelivery.PHYSICAL, loaded.getDelivery());
        assertEquals(DAY.minusDays(2), loaded.getRegistrationCutoff());
        assertTrue(loaded.isSelfRegistration());
        assertTrue(loaded.isWaitlistEnabled());
        assertFalse(loaded.isRegistrationForm());
        assertEquals(3000, loaded.getDescription().length(), "a description longer than 255 characters fits");
        assertEquals(120, loaded.durationMinutes());
    }

    @Test
    @DisplayName("The calendar query finds a date range's sessions in time order, without deleted ones")
    void calendarQuery() {
        session("Afternoon", DAY, LocalTime.of(14, 0));
        session("Morning", DAY, LocalTime.of(9, 0));
        session("Next month", DAY.plusDays(30), LocalTime.of(9, 0));
        session("Deleted", DAY, LocalTime.of(11, 0)).setDeleted(true);
        entityManager.flush();

        assertEquals(List.of("Morning", "Afternoon"), trainingProgramRepository
                .findAllByDeletedFalseAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(DAY.minusDays(1), DAY.plusDays(7))
                .stream().map(TrainingProgram::getTitle).toList());
    }

    @Test
    @DisplayName("Registrations, invitations and attendance are found per session")
    void perSession() {
        TrainingProgram a = session("A", DAY, LocalTime.of(9, 0));
        TrainingProgram b = session("B", DAY, LocalTime.of(14, 0));
        registration(a, TrainingRegistrationStatus.REGISTERED);
        registration(a, TrainingRegistrationStatus.WAITLISTED);
        invitation(b, TrainingInvitationStatus.PENDING);
        TrainingAttendance attended = new TrainingAttendance();
        attended.setTrainingId(b.getTrainingId());
        attended.setStaffId(UUID.randomUUID());
        trainingAttendanceRepository.save(attended);
        entityManager.flush();
        entityManager.clear();
        List<Long> both = List.of(a.getTrainingId(), b.getTrainingId());

        assertEquals(2, trainingRegistrationRepository.findAllByTrainingIdIn(both).size());
        assertEquals(2, trainingRegistrationRepository.findAllByTrainingId(a.getTrainingId()).size());
        assertTrue(trainingRegistrationRepository.existsByTrainingId(a.getTrainingId()));
        assertFalse(trainingRegistrationRepository.existsByTrainingId(b.getTrainingId()));
        assertEquals(List.of(b.getTrainingId()), trainingInvitationRepository.findInvitedTrainingIds(both));
        assertEquals(1, trainingInvitationRepository
                .findAllByTrainingIdAndStatus(b.getTrainingId(), TrainingInvitationStatus.PENDING).size());
        assertEquals(List.of(b.getTrainingId()), trainingAttendanceRepository.findRecordedTrainingIds(both));
        assertTrue(trainingAttendanceRepository.existsByTrainingId(b.getTrainingId()));
    }

    @Test
    @DisplayName("Invitations still unanswered when their session starts become No Response, once (FR-12-14)")
    void unansweredInvitations() {
        TrainingProgram yesterday = session("Yesterday", DAY.minusDays(1), LocalTime.of(15, 0));
        TrainingProgram started = session("Started", DAY, LocalTime.of(9, 0));
        TrainingProgram later = session("Later", DAY, LocalTime.of(14, 0));
        TrainingInvitation fromYesterday = invitation(yesterday, TrainingInvitationStatus.PENDING);
        TrainingInvitation open = invitation(started, TrainingInvitationStatus.PENDING);
        TrainingInvitation accepted = invitation(started, TrainingInvitationStatus.ACCEPTED);
        TrainingInvitation notYet = invitation(later, TrainingInvitationStatus.PENDING);
        entityManager.flush();
        LocalDateTime now = LocalDateTime.of(DAY, LocalTime.of(9, 0));

        assertEquals(2, trainingInvitationRepository.markUnansweredOnceStarted(DAY, LocalTime.of(9, 0), now));
        assertEquals(0, trainingInvitationRepository.markUnansweredOnceStarted(DAY, LocalTime.of(9, 0), now),
                "safe to run again");
        entityManager.clear();

        TrainingInvitation expired = trainingInvitationRepository.findById(open.getInvitationId()).orElseThrow();
        assertEquals(TrainingInvitationStatus.NO_RESPONSE, expired.getStatus());
        assertEquals(now, expired.getRespondedAt());
        assertEquals(TrainingInvitationStatus.NO_RESPONSE,
                trainingInvitationRepository.findById(fromYesterday.getInvitationId()).orElseThrow().getStatus());
        assertEquals(TrainingInvitationStatus.ACCEPTED,
                trainingInvitationRepository.findById(accepted.getInvitationId()).orElseThrow().getStatus());
        assertEquals(TrainingInvitationStatus.PENDING,
                trainingInvitationRepository.findById(notYet.getInvitationId()).orElseThrow().getStatus());
    }
}
