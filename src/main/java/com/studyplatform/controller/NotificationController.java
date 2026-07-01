package com.studyplatform.controller;

import com.studyplatform.dto.notification.NotificationResponse;
import com.studyplatform.security.CurrentUser;
import com.studyplatform.security.UserPrincipal;
import com.studyplatform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** Recent notifications for the current user — rebuilds the bell on reload. */
    @GetMapping("/mine")
    public ResponseEntity<List<NotificationResponse>> listMine(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.listMine(principal.getId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(principal.getId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        notificationService.markRead(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@CurrentUser UserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
