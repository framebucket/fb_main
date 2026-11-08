package com.framebucket.team.repository;

import com.framebucket.team.model.Freelancer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FreelancerRepository extends JpaRepository<Freelancer, UUID> {
    List<Freelancer> findAllByPhotographer_IdOrderByCreatedAtDesc(UUID photographerId);

    Optional<Freelancer> findByIdAndPhotographer_Id(UUID id, UUID photographerId);

    Optional<Freelancer> findByInviteTokenHash(String inviteTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select freelancer from Freelancer freelancer where freelancer.inviteTokenHash = :tokenHash")
    Optional<Freelancer> findByInviteTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
