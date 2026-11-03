package com.tbm.careerpathlearning.service;

/** Reminders for mandatory training, sent by the daily 09:00 run (FR-09-14, FR-11-16). */
public interface SopReminderService {

    /**
     * Reminds staff, in the app and by email, of mandatory courses whose last date to start or due date is 3 days away
     * or less, and once of any that are overdue. Each reminder goes once, however often this runs.
     *
     * @return how many reminders were sent
     */
    int sendReminders();
}
