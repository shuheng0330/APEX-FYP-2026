package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** The notification bell (NFR-016): everyone sees and marks only their own notifications. */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAuthority(T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName())")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> mine(Authentication authentication) {
        return ResponseEntity.ok(notificationService.mine(userId(authentication)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userId(authentication))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(id, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(Authentication authentication) {
        notificationService.markAllRead(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
