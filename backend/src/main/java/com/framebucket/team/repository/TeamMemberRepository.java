package com.framebucket.team.repository;

import com.framebucket.team.model.TeamMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findAllByPhotographer_IdOrderByCreatedAtDesc(UUID photographerId);

    Optional<TeamMember> findByIdAndPhotographer_Id(UUID id, UUID photographerId);

    Optional<TeamMember> findByInviteTokenHash(String inviteTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from TeamMember member where member.inviteTokenHash = :tokenHash")
    Optional<TeamMember> findByInviteTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
