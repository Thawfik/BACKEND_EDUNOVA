package com.studyplatform.repository;

import com.studyplatform.entity.GroupMember;
import com.studyplatform.enums.AccountType;
import com.studyplatform.enums.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findByUserId(UUID userId);

    List<GroupMember> findByGroupId(UUID groupId);

    Optional<GroupMember> findByUserIdAndGroupId(UUID userId, UUID groupId);

    boolean existsByUserIdAndGroupId(UUID userId, UUID groupId);

    long countByGroupId(UUID groupId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.group.id = :groupId AND gm.role = :role")
    List<GroupMember> findByGroupIdAndRole(UUID groupId, GroupRole role);

    /**
     * True if userId and teacherId are BOTH members of the same group
     * (regardless of who created the group).
     */
    @Query("SELECT COUNT(gm1) > 0 FROM GroupMember gm1 " +
           "WHERE gm1.user.id = :userId " +
           "AND EXISTS (" +
           "  SELECT gm2 FROM GroupMember gm2 " +
           "  WHERE gm2.group.id = gm1.group.id " +
           "  AND gm2.user.id = :teacherId" +
           ")")
    boolean isInSameGroupAs(@Param("userId") UUID userId,
                             @Param("teacherId") UUID teacherId);

    /**
     * True if the user shares at least one group with any teacher account.
     */
    @Query("SELECT COUNT(gm1) > 0 FROM GroupMember gm1 " +
           "WHERE gm1.user.id = :userId " +
           "AND EXISTS (" +
           "  SELECT gm2 FROM GroupMember gm2 " +
           "  WHERE gm2.group.id = gm1.group.id " +
           "  AND gm2.user.accountType = :teacherType" +
           ")")
    boolean isInAnyGroupWithTeacher(@Param("userId") UUID userId,
                                    @Param("teacherType") AccountType teacherType);
}