// backend/src/main/java/com/tbm/careerpathlearning/dto/JwtResponse.java
package com.tbm.careerpathlearning.dto;

public class JwtResponse {
    private String token;
    private String type = "Bearer"; // Standard JWT type

    public JwtResponse(String accessToken) {
        this.token = accessToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}