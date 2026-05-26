package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.StaffRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffRefreshTokenRepository extends JpaRepository<StaffRefreshToken, UUID> {

    @Query("SELECT srt FROM StaffRefreshToken srt WHERE srt.staffId IN (:staffIds)")
    List<StaffRefreshToken> findAllByIdIn(@Param("staffIds") Set<UUID> staffIds);

    Optional<StaffRefreshToken> getByToken(String token);
}
