package com.studyplatform.websocket;

import com.studyplatform.dto.chat.ChatMessageResponse;
import com.studyplatform.entity.StudyGroup;
import com.studyplatform.enums.NotificationType;
import com.studyplatform.repository.StudyGroupRepository;
import com.studyplatform.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket message handler for real-time features.
 *
 * Channels:
 * - /topic/chat/{groupId}     — group chat messages
 * - /topic/notes/{groupId}    — note edit broadcasts
 * - /topic/pomodoro/{groupId} — shared Pomodoro timer state
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketMessageHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final StudyGroupRepository groupRepository;

    // ── Chat ──────────────────────────────────────────────────

    @MessageMapping("/chat/{groupId}")
    @SendTo("/topic/chat/{groupId}")
    public ChatMessageResponse handleChatMessage(
            @DestinationVariable String groupId,
            WsChatMessage message) {

        log.info("WS chat in group {}: {} says '{}'",
                groupId, message.getSenderName(), message.getContent());

        return ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .groupId(UUID.fromString(groupId))
                .senderId(UUID.fromString(message.getSenderId()))
                .senderName(message.getSenderName())
                .content(message.getContent())
                .sentAt(Instant.now())
                .build();
    }

    // ── Notes ─────────────────────────────────────────────────

    @MessageMapping("/notes/{groupId}")
    @SendTo("/topic/notes/{groupId}")
    public WsNoteUpdate handleNoteUpdate(
            @DestinationVariable String groupId,
            WsNoteUpdate update) {

        log.debug("WS note update in group {}: note {} by {}",
                groupId, update.getNoteId(), update.getEditorName());
        return update;
    }

    // ── Pomodoro ──────────────────────────────────────────────

    @MessageMapping("/pomodoro/{groupId}")
    @SendTo("/topic/pomodoro/{groupId}")
    public WsPomodoroState handlePomodoroAction(
            @DestinationVariable String groupId,
            WsPomodoroState state) {

        log.info("WS pomodoro in group {}: action={}, minutes={}",
                groupId, state.getAction(), state.getMinutes());
        state.setTimestamp(Instant.now().toString());

        // On START, tell every group member a shared work session is now running —
        // even if they're reading a guide on another page.
        if ("START".equalsIgnoreCase(state.getAction())) {
            notifyGroupOfStart(groupId, state);
        }
        return state;
    }

    private void notifyGroupOfStart(String groupId, WsPomodoroState state) {
        try {
            UUID gid = UUID.fromString(groupId);
            UUID startedById = parseUuid(state.getStartedById());
            String startedBy = state.getStartedBy() != null ? state.getStartedBy() : "Un membre";
            String groupName = groupRepository.findById(gid).map(StudyGroup::getName).orElse(null);

            String message = startedBy + " a lancé un pomodoro de " + state.getMinutes() + " min"
                    + (groupName != null ? " dans « " + groupName + " »" : "")
                    + ". Rejoignez la session !";

            notificationService.notifyGroup(gid, startedById,
                    NotificationType.GROUP_POMODORO_STARTED,
                    "Session de travail en cours", message, "/groups/" + groupId);
        } catch (Exception e) {
            log.warn("Failed to notify group {} of pomodoro start: {}", groupId, e.getMessage());
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ── WebSocket message types ───────────────────────────────

    @Data
    public static class WsChatMessage {
        private String senderId;
        private String senderName;
        private String content;
    }

    @Data
    public static class WsNoteUpdate {
        private String noteId;
        private String editorId;
        private String editorName;
        private String content;
        private String action; // EDIT, CURSOR_MOVE
    }

    @Data
    public static class WsPomodoroState {
        private String action; // START, PAUSE, RESUME, RESET, COMPLETE
        private int minutes;
        private String startedBy;
        private String startedById; // initiator's user id — excluded from the START notification
        private String timestamp;
        private int remaining; // used by RESUME action
    }
}
