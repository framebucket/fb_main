package com.framebucket.event.repository;

import com.framebucket.event.model.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {
    List<EventType> findAllByPhotographerIsNullOrderByNameAsc();

    List<EventType> findAllByPhotographer_IdOrderByNameAsc(UUID photographerId);

    Optional<EventType> findByIdAndPhotographerIsNull(UUID id);

    Optional<EventType> findByIdAndPhotographer_Id(UUID id, UUID photographerId);

    boolean existsByPhotographerIsNullAndNameIgnoreCase(String name);

    boolean existsByPhotographer_IdAndNameIgnoreCase(UUID photographerId, String name);
}
