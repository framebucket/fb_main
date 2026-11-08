package com.framebucket.team.repository;

import com.framebucket.team.model.TeamContact;
import com.framebucket.team.model.TeamContactId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamContactRepository extends JpaRepository<TeamContact, TeamContactId> {
    @Modifying
    @Query(value = """
            INSERT INTO fb_team_contact (photographer_id, mobile_number, created_at)
            VALUES (:photographerId, :mobileNumber, now())
            ON CONFLICT (photographer_id, mobile_number) DO NOTHING
            """, nativeQuery = true)
    int claimContact(@Param("photographerId") java.util.UUID photographerId,
                     @Param("mobileNumber") String mobileNumber);
}
