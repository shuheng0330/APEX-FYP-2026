package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffRefreshTokenDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffRefreshTokenService {
    List<StaffRefreshTokenDto> getAllStaffRefreshTokens();

    StaffRefreshTokenDto getStaffRefreshTokenById(UUID id);

    Optional<StaffRefreshTokenDto> findStaffRefreshTokenById(UUID id);

    Optional<StaffRefreshTokenDto> getStaffRefreshTokenByToken(String token);

    List<StaffRefreshTokenDto> getStaffRefreshTokenByIdIn(Set<UUID> ids);

    StaffRefreshTokenDto updateStaffRefreshToken(UUID id, StaffRefreshTokenDto dto);

    StaffRefreshTokenDto createStaffRefreshToken(StaffRefreshTokenDto dto);

    void deleteStaffRefreshToken(UUID staffID);

    void deleteAllByStaffIdIn(Set<UUID> staffIds);

}
