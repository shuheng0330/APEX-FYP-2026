package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** One person on a session, as its trainer and HR see them: registered, waitlisted, offered, invited, or no longer. */
@Data
public class TrainingPersonDto {
    private UUID staffId;
    private String name;
    private String roleName;
    private String departmentName;
    /** REGISTERED, WAITLISTED, OFFERED or CANCELLED; or, for someone invited but not registered, INVITED, DECLINED or NO_RESPONSE. */
    private String status;
    /** Null for someone invited who hasn't registered. */
    private Long registrationId;
    private Integer waitlistPosition;
    private LocalDateTime offerExpiresAt;
    private LocalDateTime registeredAt;
    /** Why the registration ended, or why the invitation was declined. */
    private String note;
    /** The trainer or HR who added the person, if they didn't register themselves. */
    private String addedByName;
    /** HRDC details (FR-13-03), for the session's trainer and HR only; the IC or passport number shows its last 4 characters. */
    private String idNumber;
    private String phone;
    private String branch;
}
