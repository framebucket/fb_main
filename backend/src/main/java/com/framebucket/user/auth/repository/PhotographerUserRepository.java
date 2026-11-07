package com.framebucket.user.auth.repository;

import com.framebucket.user.auth.model.PhotographerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PhotographerUserRepository extends JpaRepository<PhotographerUser, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select photographer from PhotographerUser photographer where photographer.id = :id")
    Optional<PhotographerUser> findByIdForUpdate(@Param("id") UUID id);

    Optional<PhotographerUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobileNumber(String mobileNumber);

    List<PhotographerUser> findAllByMobileNumber(String mobileNumber);

    List<PhotographerUser> findAllBySubIdIsNull();
}
