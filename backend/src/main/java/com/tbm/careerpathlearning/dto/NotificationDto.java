package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class NotificationDto {
    private Long id;
    private String type;
    private String title;
    private String body;
    /** The page in the app the notification opens; null when there is none. */
    private String link;
    private OffsetDateTime createdAt;
    private boolean read;
}
