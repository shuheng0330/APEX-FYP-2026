package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** How many staff a session's audience covers, against its seats (FR-12-15). */
@Data
public class TrainingAudienceCheckDto {
    private int audienceSize;
    private int capacity;
    /** Set when a mandatory session can't seat everyone in its audience. */
    private String warning;
}
