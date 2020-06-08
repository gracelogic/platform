package com.gracelogic.platform.oauth.dto;

import java.util.UUID;

public class TokenByCodeRequestDTO {
    private UUID authProviderId;
    private String code;
    private String accessToken;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UUID getAuthProviderId() {
        return authProviderId;
    }

    public void setAuthProviderId(UUID authProviderId) {
        this.authProviderId = authProviderId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
