package com.studyplatform.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * One entry in a group's recent-activity feed (work sessions, collaborative chat,
 * shared notes…). Shown on the group overview tab in place of the old collective
 * statistics. {@code type} is one of SESSION | CHAT | NOTE.
 */
@Data
@Builder
public class GroupActivityResponse {
    private String type;
    private String title;
    private String actorName;
    private Instant at;
}