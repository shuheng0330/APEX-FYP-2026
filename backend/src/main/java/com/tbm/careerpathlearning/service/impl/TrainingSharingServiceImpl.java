package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSharingBoardDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionTiming;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAccessRequest;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAccessRequestRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import com.tbm.careerpathlearning.service.TrainingService;
import com.tbm.careerpathlearning.service.TrainingSharingService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrainingSharingServiceImpl implements TrainingSharingService {

    @Autowired private TrainingProgramRepository trainingProgramRepository;
    @Autowired private TrainingRegistrationRepository trainingRegistrationRepository;
    @Autowired private TrainingAttendanceRepository trainingAttendanceRepository;
    @Autowired private TrainingAccessRequestRepository trainingAccessRequestRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private TrainerAccessService trainerAccessService;
    @Autowired private NotificationService notificationService;
    @Autowired private TrainingAuditService trainingAuditService;
    @Autowired private TrainingService trainingService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TrainingSharingBoardDto board(Integer year, TrainingViewer viewer) {
        LocalDate today = LocalDate.now(clock);
        int y = year == null ? today.getYear() : year;
        if (y < 2000 || y > today.getYear() + 1) {
            throw new BadRequestException("Choose a year from the list.");
        }
        List<TrainingProgram> sharing = trainingProgramRepository.findAll().stream()
                .filter(s -> !s.isDeleted() && s.getKind() == TrainingSessionKind.STAFF_SHARING
                        && s.getStatus() != TrainingSessionStatus.CANCELLED)
                .toList();
        // Only sessions whose attendance is submitted count: until then the numbers can change (FR-14-09).
        List<TrainingProgram> held = sharing.stream()
                .filter(s -> s.getSessionDate().getYear() == y && s.getAttendanceSubmittedAt() != null)
                .sorted(Comparator.comparing(TrainingProgram::startsAt).reversed())
                .toList();
        List<Long> ids = held.stream().map(TrainingProgram::getTrainingId).toList();
        Map<Long, List<TrainingAttendance>> marks = ids.isEmpty() ? Map.of()
                : trainingAttendanceRepository.findAllByTrainingIdIn(ids).stream()
                .collect(Collectors.groupingBy(TrainingAttendance::getTrainingId));
        Map<Long, List<TrainingRegistration>> registrations = ids.isEmpty() ? Map.of()
                : trainingRegistrationRepository.findAllByTrainingIdIn(ids).stream()
                .collect(Collectors.groupingBy(TrainingRegistration::getTrainingId));

        Map<UUID, Staff> active = new LinkedHashMap<>();
        staffRepository.findAllActiveWithRole().forEach(s -> active.put(s.getId(), s));
        Set<UUID> presenterIds = held.stream().map(TrainingProgram::getTrainerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Staff> presenters = presenterIds.isEmpty() ? Map.of()
                : staffRepository.findAllWithRoleByIdIn(presenterIds).stream().collect(Collectors.toMap(Staff::getId, s -> s));

        TrainingSharingBoardDto dto = new TrainingSharingBoardDto();
        dto.setYear(y);
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(today.getYear());
        sharing.forEach(s -> years.add(s.getSessionDate().getYear()));
        dto.setYears(new ArrayList<>(years));

        // The company-wide figures (FR-15-02): attendances and distinct staff reached are counted apart (TF-23).
        Set<UUID> reached = new HashSet<>();
        List<Integer> scores = new ArrayList<>();
        int attendances = 0;
        Map<UUID, List<TrainingProgram>> byPresenter = new LinkedHashMap<>();
        Staff me = active.get(viewer.userId());
        for (TrainingProgram s : held) {
            List<TrainingAttendance> rows = marks.getOrDefault(s.getTrainingId(), List.of());
            List<TrainingAttendance> came = rows.stream().filter(a -> a.getStatus() == TrainingAttendanceStatus.ATTENDED).toList();
            attendances += came.size();
            came.stream().map(TrainingAttendance::getStaffId).filter(active::containsKey).forEach(reached::add);
            came.stream().map(TrainingAttendance::getQuizScore).filter(Objects::nonNull).forEach(scores::add);
            if (s.getTrainerId() != null) {
                byPresenter.computeIfAbsent(s.getTrainerId(), k -> new ArrayList<>()).add(s);
            }
            Set<UUID> onList = new HashSet<>();
            registrations.getOrDefault(s.getTrainingId(), List.of()).stream()
                    .filter(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED)
                    .map(TrainingRegistration::getStaffId).forEach(onList::add);
            rows.forEach(a -> onList.add(a.getStaffId()));
            // Decision A: a session's audience decides who sees it, here as on the calendar.
            if (viewer.seesAllSessions() || onList.contains(viewer.userId())
                    || (me != null && TrainingServiceImpl.inAudience(s, me))) {
                TrainingSharingBoardDto.Held h = new TrainingSharingBoardDto.Held();
                h.setId(s.getTrainingId());
                h.setTitle(s.getTitle());
                h.setDate(s.getSessionDate());
                h.setStartTime(s.getStartTime());
                h.setEndTime(s.getEndTime());
                h.setPresenterName(name(presenters, s.getTrainerId()));
                h.setAttended(came.size());
                h.setOnList(onList.size());
                dto.getSessions().add(h);
            }
        }
        dto.setSessionsHeld(held.size());
        dto.setPresenters(byPresenter.size());
        dto.setTotalAttendances(attendances);
        dto.setStaffReached(reached.size());
        dto.setHeadcount(active.size());
        dto.setReachPercent(active.isEmpty() ? null : Math.round(reached.size() * 100f / active.size()));
        dto.setAverageAttendance(held.isEmpty() ? null : Math.round(attendances * 10.0 / held.size()) / 10.0);
        dto.setAverageQuizScore(average(scores));

        byPresenter.forEach((presenterId, given) -> {
            Staff person = presenters.get(presenterId);
            TrainingSharingBoardDto.Presenter p = new TrainingSharingBoardDto.Presenter();
            p.setStaffId(presenterId);
            p.setName(person == null ? "Unknown user" : person.getName());
            if (person != null) {
                OrgChart department = SopAssignmentServiceImpl.department(person);
                p.setRoleName(person.getRole() == null ? null : person.getRole().getName());
                p.setDepartmentName(department == null ? null : department.getName());
            }
            p.setLeftCompany(!active.containsKey(presenterId));
            Set<UUID> theirs = new HashSet<>();
            List<Integer> theirScores = new ArrayList<>();
            int theirAttendances = 0;
            for (TrainingProgram s : given) {
                for (TrainingAttendance a : marks.getOrDefault(s.getTrainingId(), List.of())) {
                    if (a.getStatus() != TrainingAttendanceStatus.ATTENDED) continue;
                    theirAttendances++;
                    theirs.add(a.getStaffId());
                    if (a.getQuizScore() != null) theirScores.add(a.getQuizScore());
                }
            }
            p.setSessions(given.size());
            p.setAttendances(theirAttendances);
            p.setStaffReached(theirs.size());
            p.setAverageQuizScore(average(theirScores));
            p.setLastShared(given.stream().map(TrainingProgram::getSessionDate).max(Comparator.naturalOrder()).orElse(null));
            dto.getStaffWhoShared().add(p);
        });
        dto.getStaffWhoShared().sort(Comparator.comparing(TrainingSharingBoardDto.Presenter::getSessions).reversed()
                .thenComparing(Comparator.comparing(TrainingSharingBoardDto.Presenter::getAttendances).reversed())
                .thenComparing(p -> Objects.toString(p.getName(), ""), String.CASE_INSENSITIVE_ORDER));

        // What is still to come, as the viewer's calendar shows it.
        dto.setUpcoming(trainingService.calendar(today, today.plusDays(365), false, viewer).stream()
                .filter(s -> s.getKind() == TrainingSessionKind.STAFF_SHARING && s.getStatus() == TrainingSessionStatus.SCHEDULED
                        && s.getTiming() != TrainingSessionTiming.ENDED)
                .toList());
        return dto;
    }

    @Override
    @Transactional
    public TrainingSessionDto requestTrainerAccess(Long sessionId, TrainingViewer viewer) {
        TrainingProgram s = trainingProgramRepository.findByTrainingIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
        if (!TrainingAttendanceServiceImpl.sharingFollowsOn(s) || s.getAttendanceSubmittedAt() == null) {
            throw new BadRequestException("Trainer access can be asked for from a Product Training session you attended, "
                    + "once its attendance is submitted.");
        }
        boolean attended = trainingAttendanceRepository.findAllByStaffIdAndTrainingIdIn(viewer.userId(), List.of(sessionId))
                .stream().anyMatch(a -> a.getStatus() == TrainingAttendanceStatus.ATTENDED);
        if (!attended) {
            throw new BadRequestException("Only staff who attended this session can ask for Trainer access from it.");
        }
        if (viewer.canSchedule()) {
            throw new BadRequestException("You can already present: schedule a staff sharing session from the Training Calendar.");
        }
        if (trainingAccessRequestRepository.existsByStaffIdAndTrainingId(viewer.userId(), sessionId)) {
            throw new BadRequestException("You've already asked. The Super Admin has been told.");
        }
        Staff me = staffRepository.findAllWithRoleByIdIn(List.of(viewer.userId())).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Your account couldn't be found."));
        TrainingAccessRequest request = new TrainingAccessRequest();
        request.setStaffId(viewer.userId());
        request.setTrainingId(sessionId);
        request.setRequestedAt(LocalDateTime.now(clock));
        trainingAccessRequestRepository.save(request);

        String after = "\"" + s.getTitle() + "\" on " + s.getSessionDate().format(TrainingSpreadsheets.DAY);
        trainingAuditService.record(viewer.userId(), TrainingAuditAction.TRAINER_ACCESS_REQUESTED, TrainingAuditTarget.STAFF,
                viewer.userId().toString(), null, "After " + after);
        String name = Objects.toString(me.getName(), "A staff member");
        // %20, not +, so the Trainer Access page reads the name back with its spaces.
        String link = "/sop/trainer-access?search=" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        for (Staff admin : trainerAccessService.superAdmins()) {
            notificationService.notify(admin.getId(), NotificationType.TRAINER_ACCESS_REQUESTED, "Trainer access requested: " + name,
                    name + " attended " + after + " and would like to present a staff sharing session. You can give "
                            + "Trainer access, with an end date, on the Trainer Access page.", link);
        }
        return trainingService.get(sessionId, viewer);
    }

    private static Integer average(List<Integer> scores) {
        return scores.isEmpty() ? null : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static String name(Map<UUID, Staff> staff, UUID id) {
        Staff s = id == null ? null : staff.get(id);
        return s == null ? "Unknown user" : s.getName();
    }
}
