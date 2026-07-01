package com.studyplatform.enums;

/**
 * Kinds of in-app notification. Only {@link #GROUP_POMODORO_STARTED} is wired up
 * today; the others are placeholders so new triggers (chat mentions, scheduled
 * sessions…) can be added without touching the rest of the module.
 */
public enum NotificationType {
    GROUP_POMODORO_STARTED,
    GROUP_SESSION_SCHEDULED,
    GROUP_MEMBER_ADDED,
    CHAT_MENTION
}
