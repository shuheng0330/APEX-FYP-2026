package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SopReminderServiceImpl implements SopReminderService {

    /** How far ahead a reminder comes (FR-09-14). */
    static final int DAYS_AHEAD = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private Clock clock;

    @Override
    public int sendReminders() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, Optional<SopDocument>> courses = new HashMap<>();
        int sent = 0;
        for (SopEnrollment e : sopEnrollmentRepository.findAll()) {
            // Optional courses are never overdue and get no reminders (FR-11-17).
            if (!e.isMandatory() || !e.isActive()
                    || (e.getEffectiveDate() != null && e.getEffectiveDate().isAfter(today))) {
                continue;
            }
            LocalDate due = e.dueDate();
            SopDocument doc = courses.computeIfAbsent(e.getSopDocumentId(), sopDocumentRepository::findById).orElse(null);
            if (due == null || doc == null || doc.isDeleted() || !doc.isLive()
                    || staffRepository.findByIdAndIsDeletedFalse(e.getStaffId()).isEmpty()) {
                continue;
            }
            // In the Learning Portal mode an unstarted course's date is the last date to start it (FR-09-03).
            boolean toStart = e.getMode() == SopAssignmentMode.PORTAL && e.getStatus() == SopEnrollmentStatus.NOT_STARTED;
            String date = due.format(DATE);
            String link = "/my-learning/course/" + doc.getId();
            boolean done;
            if (due.isBefore(today)) {
                done = notificationService.notifyOnce("sop-overdue:" + e.getId(), e.getStaffId(), NotificationType.COURSE_OVERDUE,
                        "Training overdue", doc.getTitle() + (toStart ? " should have been started by " : " was due on ") + date
                                + ". It is still open, so please finish it as soon as you can.", link);
            } else if (!due.isAfter(today.plusDays(DAYS_AHEAD))) {
                done = toStart
                        ? notificationService.notifyOnce("sop-start-soon:" + e.getId() + ":" + due, e.getStaffId(),
                                NotificationType.COURSE_DUE_SOON, "Start your training",
                                "The last day to start " + doc.getTitle() + " is " + date + ".", link)
                        : notificationService.notifyOnce("sop-due-soon:" + e.getId() + ":" + due, e.getStaffId(),
                                NotificationType.COURSE_DUE_SOON, "Training due soon", doc.getTitle() + " is due on " + date + ".", link);
            } else {
                continue;
            }
            sent += done ? 1 : 0;
        }
        return sent;
    }
}
