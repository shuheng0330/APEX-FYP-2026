package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** What a staff member sends to register. The HRDC details are only for sessions that ask for the form (FR-13-03). */
@Data
public class TrainingRegistrationRequest {
    /** IC or passport number. */
    private String idNumber;
    private String phone;
    /** One of TBM's branches. */
    private String branch;
    /** The person agreed to the PDPA notice. */
    private boolean pdpaConsent;
}
