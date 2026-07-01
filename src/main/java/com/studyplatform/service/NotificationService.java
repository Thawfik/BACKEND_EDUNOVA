package com.studyplatform.service;

import com.studyplatform.dto.notification.NotificationResponse;
import com.studyplatform.entity.GroupMember;
import com.studyplatform.entity.Notification;
import com.studyplatform.entity.User;
import com.studyplatform.enums.NotificationType;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.GroupMemberRepository;
import com.studyplatform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Central bookkeeping for in-app notifications. Every notification is persisted
 * and pushed to its recipient over WebSocket ({@code /topic/notifications/{userId}})
 * so the frontend bell updates live without polling. Mirrors {@link JobService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** Create and deliver a single notification to one user. */
    @Transactional
    public Notification notify(User recipient, NotificationType type, String title,
                               String message, UUID groupId, String linkPath) {
        Notification n = Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .groupId(groupId)
                .linkPath(linkPath)
                .build();
        n = notificationRepository.save(n);
        broadcast(n);
        return n;
    }

    /**
     * Notify every member of a group (optionally excluding the initiator). Used
     * when a shared work session starts so the whole group is told at once.
     */
    @Transactional
    public void notifyGroup(UUID groupId, UUID excludeUserId, NotificationType type,
                            String title, String message, String linkPath) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        for (GroupMember member : members) {
            User recipient = member.getUser();
            if (excludeUserId != null && recipient.getId().equals(excludeUserId)) {
                continue;
            }
            notify(recipient, type, title, message, groupId, linkPath);
        }
        log.info("Notified {} member(s) of group {} — {}", members.size(), groupId, title);
    }

    public List<NotificationResponse> listMine(UUID userId) {
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        Notification n = notificationRepository.findByIdAndRecipientId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadForUser(userId);
    }

    private void broadcast(Notification n) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + n.getRecipient().getId(), toResponse(n));
        } catch (Exception e) {
            log.warn("Failed to broadcast notification {}: {}", n.getId(), e.getMessage());
        }
    }

    public NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .groupId(n.getGroupId())
                .linkPath(n.getLinkPath())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
