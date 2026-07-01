package com.studyplatform.service;

import com.studyplatform.dto.teacher.GroupOverview;
import com.studyplatform.dto.teacher.StudentOverview;
import com.studyplatform.dto.teacher.TeacherDashboard;
import com.studyplatform.entity.User;
import com.studyplatform.enums.AccountType;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final GroupMemberRepository memberRepository;
    private final StudyGroupRepository groupRepository;
    private final XpLogRepository xpLogRepository;
    private final StudyGuideRepository guideRepository;
    private final QuizAttemptRepository attemptRepository;
    private final BadgeService badgeService;

    private static final double STRUGGLING_SCORE_THRESHOLD = 60.0;

    public void requireTeacher(User user) {
        if (user.getAccountType() != AccountType.TEACHER) {
            throw ApiException.forbidden("This endpoint is restricted to teachers");
        }
    }

    public List<GroupOverview> getMyGroups(User teacher) {
        requireTeacher(teacher);
        return memberRepository.findByUserId(teacher.getId()).stream()
                .map(gm -> buildGroupOverview(gm.getGroup().getId()))
                .toList();
    }

    public GroupOverview getGroupDetail(UUID groupId, User teacher) {
        requireTeacher(teacher);
        memberRepository.findByUserIdAndGroupId(teacher.getId(), groupId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group"));
        return buildGroupOverview(groupId);
    }

    /**
     * Aggregate view across every group the teacher belongs to: global KPIs, the
     * per-group breakdown and the de-duplicated list of all their students (each
     * carrying the names of the teacher's groups it belongs to).
     */
    public TeacherDashboard getDashboard(User teacher) {
        requireTeacher(teacher);

        List<GroupOverview> groups = memberRepository.findByUserId(teacher.getId()).stream()
                .map(gm -> buildGroupOverview(gm.getGroup().getId()))
                .toList();

        // De-duplicate students who appear in several of the teacher's groups,
        // collecting every group name they belong to.
        Map<UUID, StudentOverview> byId = new LinkedHashMap<>();
        Map<UUID, List<String>> groupsByStudent = new LinkedHashMap<>();
        for (GroupOverview g : groups) {
            for (StudentOverview s : g.getStudents()) {
                byId.putIfAbsent(s.getUserId(), s);
                groupsByStudent.computeIfAbsent(s.getUserId(), k -> new ArrayList<>()).add(g.getGroupName());
            }
        }
        List<StudentOverview> students = new ArrayList<>(byId.values());
        students.forEach(s -> s.setGroupNames(groupsByStudent.get(s.getUserId())));

        int totalXp = students.stream().mapToInt(StudentOverview::getTotalXp).sum();
        int totalQuizzes = students.stream().mapToInt(StudentOverview::getQuizzesTaken).sum();
        int totalGuides = students.stream().mapToInt(StudentOverview::getGuidesCompleted).sum();
        Double avgScore = roundAverage(students);
        int struggling = (int) students.stream().filter(s -> "STRUGGLING".equals(s.getStatus())).count();
        int inactive = (int) students.stream().filter(s -> "INACTIVE".equals(s.getStatus())).count();
        int active = students.size() - struggling - inactive;

        return TeacherDashboard.builder()
                .totalGroups(groups.size())
                .totalStudents(students.size())
                .totalXp(totalXp)
                .averageQuizScore(avgScore)
                .strugglingCount(struggling)
                .inactiveCount(inactive)
                .activeCount(active)
                .totalQuizzesTaken(totalQuizzes)
                .totalGuidesCompleted(totalGuides)
                .groups(groups)
                .students(students)
                .build();
    }

    public List<StudentOverview> identifyStrugglingStudents(UUID groupId, User teacher) {
        requireTeacher(teacher);
        memberRepository.findByUserIdAndGroupId(teacher.getId(), groupId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group"));

        return studentsOf(groupId).stream()
                .filter(s -> "STRUGGLING".equals(s.getStatus()) || "INACTIVE".equals(s.getStatus()))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Student members of a group (teachers — including the viewer — are excluded). */
    private List<StudentOverview> studentsOf(UUID groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .filter(gm -> gm.getUser().getAccountType() == AccountType.STUDENT)
                .map(gm -> buildStudentOverview(gm.getUser()))
                .toList();
    }

    private GroupOverview buildGroupOverview(UUID groupId) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));
        var members = memberRepository.findByGroupId(groupId);

        List<StudentOverview> students = members.stream()
                .filter(gm -> gm.getUser().getAccountType() == AccountType.STUDENT)
                .map(gm -> buildStudentOverview(gm.getUser()))
                .toList();

        int totalXp = students.stream().mapToInt(StudentOverview::getTotalXp).sum();
        int struggling = (int) students.stream().filter(s -> "STRUGGLING".equals(s.getStatus())).count();
        int inactive = (int) students.stream().filter(s -> "INACTIVE".equals(s.getStatus())).count();

        return GroupOverview.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .memberCount(members.size())
                .studentCount(students.size())
                .totalGroupXp(totalXp)
                .averageQuizScore(roundAverage(students))
                .strugglingCount(struggling)
                .inactiveCount(inactive)
                .students(students)
                .createdAt(group.getCreatedAt())
                .build();
    }

    private StudentOverview buildStudentOverview(User user) {
        int xp = xpLogRepository.getTotalXpByUserId(user.getId());
        long guides = guideRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).size();
        long quizzes = attemptRepository.countByUserId(user.getId());
        Double avgScore = attemptRepository.findAverageScoreByUserId(user.getId());
        int level = badgeService.getLevel(user.getId()).getLevel();

        String status;
        if (avgScore != null && avgScore < STRUGGLING_SCORE_THRESHOLD) {
            status = "STRUGGLING";
        } else if (xp == 0 && guides == 0 && quizzes == 0) {
            status = "INACTIVE";
        } else {
            status = "ACTIVE";
        }

        return StudentOverview.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .totalXp(xp)
                .level(level)
                .guidesCompleted((int) guides)
                .quizzesTaken((int) quizzes)
                .averageQuizScore(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : null)
                .status(status)
                .build();
    }

    private Double roundAverage(List<StudentOverview> students) {
        double avg = students.stream()
                .filter(s -> s.getAverageQuizScore() != null)
                .mapToDouble(StudentOverview::getAverageQuizScore)
                .average().orElse(0.0);
        return Math.round(avg * 10.0) / 10.0;
    }
}