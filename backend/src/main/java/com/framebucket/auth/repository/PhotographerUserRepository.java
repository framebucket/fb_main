package com.framebucket.auth.repository;

import com.framebucket.auth.model.PhotographerUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhotographerUserRepository extends JpaRepository<PhotographerUser, UUID> {
    Optional<PhotographerUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
