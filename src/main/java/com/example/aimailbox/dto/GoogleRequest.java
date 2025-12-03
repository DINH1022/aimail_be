package com.example.aimailbox.dto;

import lombok.Data;

@Data
public class GoogleRequest {
    private String idToken;
    private String scope;
    private String accessToken;

    public String getIdToken() {
        return this.idToken;
    }

    public String getScope() {
        return this.scope;
    }

    public String getAccessToken() {
        return this.accessToken;
    }
}
