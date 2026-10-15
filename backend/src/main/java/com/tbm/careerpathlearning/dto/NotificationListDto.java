package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;

/** The bell: the unread count and the 30 newest notifications. */
@Data
public class NotificationListDto {
    private long unread;
    private List<NotificationDto> items;
}
