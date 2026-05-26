package com.tbm.careerpathlearning.service;

import io.jsonwebtoken.Claims;

import java.util.List;
import java.util.UUID;

public interface TokenService {

    String generateAccessToken(UUID userId, List<String> roles);

    String generateRefreshToken();

    Claims extractClaims(String token);
}


