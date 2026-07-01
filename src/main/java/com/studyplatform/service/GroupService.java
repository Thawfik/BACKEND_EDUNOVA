package com.studyplatform.service;

import com.studyplatform.dto.group.*;
import com.studyplatform.entity.*;
import com.studyplatform.enums.GroupRole;
import com.studyplatform.enums.NotificationType;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final StudyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final WorkSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NoteRepository noteRepository;
    private final NotificationService notificationService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ── Create group ──────────────────────────────────────────

    @Transactional
    public GroupResponse create(User creator, CreateGroupRequest request) {
        StudyGroup group = StudyGroup.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .inviteCode(generateUniqueCode())
                .createdBy(creator)
                .build();

        group = groupRepository.save(group);

        GroupMember membership = GroupMember.builder()
                .user(creator)
                .group(group)
                .role(GroupRole.ADMIN)
                .build();
        memberRepository.save(membership);

        log.info("Group '{}' created by {}", group.getName(), creator.getEmail());
        return toResponse(group, creator.getId());
    }

    // ── Join via invite code ──────────────────────────────────

    @Transactional
    public GroupResponse join(User user, JoinGroupRequest request) {
        StudyGroup group = groupRepository.findByInviteCode(request.getInviteCode().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Invalid invite code"));

        if (memberRepository.existsByUserIdAndGroupId(user.getId(), group.getId())) {
            throw ApiException.conflict("You are already a member of this group");
        }

        GroupMember membership = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupRole.MEMBER)
                .build();
        memberRepository.save(membership);

        log.info("User {} joined group '{}'", user.getEmail(), group.getName());
        return toResponse(group, user.getId());
    }

    // ── Preview group (by invite code, no membership required) ───────────────

    public GroupResponse previewGroup(String inviteCode) {
        StudyGroup group = groupRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Invalid invite code"));

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .memberCount(memberRepository.countByGroupId(group.getId()))
                .createdAt(group.getCreatedAt())
                .build();
    }

    // ── Leave group ───────────────────────────────────────────

    @Transactional
    public void leave(UUID userId, UUID groupId) {
        GroupMember membership = memberRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> ApiException.notFound("You are not a member of this group"));

        if (membership.getRole() == GroupRole.ADMIN) {
            long adminCount = memberRepository.findByGroupIdAndRole(groupId, GroupRole.ADMIN).size();
            if (adminCount <= 1) {
                long totalMembers = memberRepository.countByGroupId(groupId);
                if (totalMembers > 1) {
                    throw ApiException.badRequest(
                            "You are the only admin. Promote another member before leaving.");
                }
                // Last member leaving — delete the group
                memberRepository.delete(membership);
                groupRepository.deleteById(groupId);
                log.info("Group {} deleted (last member left)", groupId);
                return;
            }
        }

        memberRepository.delete(membership);
        log.info("User {} left group {}", userId, groupId);
    }

    // ── List my groups ────────────────────────────────────────

    public List<GroupResponse> listMyGroups(UUID userId) {
        return memberRepository.findByUserId(userId).stream()
                .map(gm -> toResponse(gm.getGroup(), userId))
                .toList();
    }

    // ── Get group detail ──────────────────────────────────────

    public GroupResponse getById(UUID groupId, UUID userId) {
        requireMembership(userId, groupId);
        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));
        return toResponse(group, userId);
    }

    // ── List members ──────────────────────────────────────────

    public List<GroupMemberResponse> listMembers(UUID groupId, UUID userId) {
        requireMembership(userId, groupId);
        return memberRepository.findByGroupId(groupId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    // ── Remove member (admin only) ────────────────────────────

    @Transactional
    public void removeMember(UUID groupId, UUID targetUserId, UUID requesterId) {
        GroupMember requester = requireMembership(requesterId, groupId);
        if (requester.getRole() != GroupRole.ADMIN) {
            throw ApiException.forbidden("Only admins can remove members");
        }

        if (targetUserId.equals(requesterId)) {
            throw ApiException.badRequest("Use the leave endpoint to leave the group");
        }

        GroupMember target = memberRepository.findByUserIdAndGroupId(targetUserId, groupId)
                .orElseThrow(() -> ApiException.notFound("User is not a member of this group"));

        memberRepository.delete(target);
        log.info("User {} removed from group {} by admin {}", targetUserId, groupId, requesterId);
    }

    // ── Promote to admin ──────────────────────────────────────

    @Transactional
    public void promoteMember(UUID groupId, UUID targetUserId, UUID requesterId) {
        GroupMember requester = requireMembership(requesterId, groupId);
        if (requester.getRole() != GroupRole.ADMIN) {
            throw ApiException.forbidden("Only admins can promote members");
        }

        GroupMember target = memberRepository.findByUserIdAndGroupId(targetUserId, groupId)
                .orElseThrow(() -> ApiException.notFound("User is not a member of this group"));

        target.setRole(GroupRole.ADMIN);
        memberRepository.save(target);
    }

    // ── Add member by email (admin only) ──────────────────────

    /**
     * Admin directly adds a registered user to the group by their email. The new
     * member is notified in-app so they discover the group (and its tabs) right away.
     */
    @Transactional
    public GroupMemberResponse addMemberByEmail(UUID groupId, UUID requesterId, String email) {
        GroupMember requester = requireMembership(requesterId, groupId);
        if (requester.getRole() != GroupRole.ADMIN) {
            throw ApiException.forbidden("Only admins can add members");
        }

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("No account is registered with this email"));

        if (memberRepository.existsByUserIdAndGroupId(user.getId(), group.getId())) {
            throw ApiException.conflict("This user is already a member of the group");
        }

        GroupMember membership = memberRepository.save(GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupRole.MEMBER)
                .build());

        notificationService.notify(
                user,
                NotificationType.GROUP_MEMBER_ADDED,
                "Ajouté à un groupe",
                "Vous avez été ajouté au groupe « " + group.getName() + " ».",
                group.getId(),
                "/groups/" + group.getId());

        log.info("User {} added to group '{}' by admin {}", user.getEmail(), group.getName(), requesterId);
        return toMemberResponse(membership);
    }

    // ── Recent activity feed ──────────────────────────────────

    /**
     * Unified, time-sorted feed of the group's recent activity: scheduled work
     * sessions, collaborative chat messages and notes shared with the group.
     */
    public List<GroupActivityResponse> getRecentActivity(UUID groupId, UUID userId, int limit) {
        requireMembership(userId, groupId);
        List<GroupActivityResponse> feed = new ArrayList<>();

        sessionRepository.findByGroupIdOrderByScheduledAtDesc(groupId).stream()
                .limit(limit)
                .forEach(ws -> feed.add(GroupActivityResponse.builder()
                        .type("SESSION")
                        .title(ws.getTitle() != null && !ws.getTitle().isBlank()
                                ? ws.getTitle() : "Session de travail")
                        .actorName(ws.getCreatedBy().getFullName())
                        .at(ws.getCreatedAt())
                        .build()));

        chatMessageRepository.findByGroupIdOrderBySentAtDesc(groupId, PageRequest.of(0, limit))
                .forEach(m -> feed.add(GroupActivityResponse.builder()
                        .type("CHAT")
                        .title(truncate(m.getContent(), 90))
                        .actorName(m.getSender().getFullName())
                        .at(m.getSentAt())
                        .build()));

        noteRepository.findByGroupIdAndSharedWithGroupTrueOrderByUpdatedAtDesc(groupId).stream()
                .limit(limit)
                .forEach(n -> feed.add(GroupActivityResponse.builder()
                        .type("NOTE")
                        .title(n.getTitle())
                        .actorName(n.getAuthor().getFullName())
                        .at(n.getUpdatedAt())
                        .build()));

        return feed.stream()
                .filter(a -> a.getAt() != null)
                .sorted(Comparator.comparing(GroupActivityResponse::getAt).reversed())
                .limit(limit)
                .toList();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }

    // ── Schedule work session ─────────────────────────────────

    @Transactional
    public WorkSessionResponse scheduleSession(UUID groupId, User user, ScheduleSessionRequest request) {
        requireMembership(user.getId(), groupId);
        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));

        WorkSession session = WorkSession.builder()
                .group(group)
                .createdBy(user)
                .title(request.getTitle())
                .scheduledAt(request.getScheduledAt())
                .durationMinutes(request.getDurationMinutes())
                .active(false)
                .build();

        return toSessionResponse(sessionRepository.save(session));
    }

    // ── List sessions ─────────────────────────────────────────

    public List<WorkSessionResponse> listUpcomingSessions(UUID groupId, UUID userId) {
        requireMembership(userId, groupId);
        return sessionRepository.findByGroupIdAndScheduledAtAfterOrderByScheduledAtAsc(groupId, Instant.now())
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────

    public GroupMember requireMembership(UUID userId, UUID groupId) {
        return memberRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group"));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique invite code");
    }

    private GroupResponse toResponse(StudyGroup group, UUID requestingUserId) {
        GroupMember membership = memberRepository.findByUserIdAndGroupId(requestingUserId, group.getId())
                .orElse(null);

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .inviteCode(group.getInviteCode())
                .createdByName(group.getCreatedBy().getFullName())
                .memberCount(memberRepository.countByGroupId(group.getId()))
                .myRole(membership != null ? membership.getRole().name() : null)
                .createdAt(group.getCreatedAt())
                .build();
    }

    private GroupMemberResponse toMemberResponse(GroupMember gm) {
        User u = gm.getUser();
        return GroupMemberResponse.builder()
                .id(gm.getId())
                .userId(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .role(gm.getRole().name())
                .joinedAt(gm.getJoinedAt())
                .build();
    }

    private WorkSessionResponse toSessionResponse(WorkSession ws) {
        return WorkSessionResponse.builder()
                .id(ws.getId())
                .title(ws.getTitle())
                .scheduledAt(ws.getScheduledAt())
                .durationMinutes(ws.getDurationMinutes())
                .active(ws.isActive())
                .createdByName(ws.getCreatedBy().getFullName())
                .createdAt(ws.getCreatedAt())
                .build();
    }
}
