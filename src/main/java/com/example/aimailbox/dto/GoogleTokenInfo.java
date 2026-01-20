package com.example.aimailbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GoogleTokenInfo {
    // Fields returned by Google's tokeninfo endpoint
    private String iss;
    private String azp;
    private String aud;
    private String sub;
    private String email;
    @JsonProperty("email_verified")
    private String emailVerified; // mapped from JSON key "email_verified"
    private String name;
    private String picture;
    private String given_name;
    private String family_name;
    private String locale;
    private String iat;
    private String exp;

    // Lombok @Data generates getters/setters
}
