package com.example.aimailbox.controller;

import com.example.aimailbox.dto.AuthResponse;
import com.example.aimailbox.dto.GoogleRequest;
import com.example.aimailbox.service.GoogleAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class GoogleAuthController {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthController.class);

    private final GoogleAuthService googleAuthService;

    public GoogleAuthController(GoogleAuthService googleAuthService) {
        this.googleAuthService = googleAuthService;
    }

    /**
     * Step 1: Redirect user to Google authorization screen
     */
    @GetMapping("/auth/google/authorize")
    public ResponseEntity<Void> authorize(@RequestParam(required = false) String state) {
        if (googleAuthService.isMisconfigured()) {
            // Misconfiguration: return 500 so frontend can show error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        URI uri = googleAuthService.buildAuthorizationUri(state);

        // Log the URI to the console for easier testing
        logger.info("Google authorization URI: {}", uri);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(uri)
                .build();
    }

    /**
     * Step 2: Google redirects back here with ?code=
     *
     * Instead of redirecting to the frontend with tokens in the URL fragment or HTML,
     * return the tokens as JSON so the frontend can consume them directly.
     */
    @GetMapping("/auth/google/callback")
    public ResponseEntity<AuthResponse> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        if (googleAuthService.isMisconfigured()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        try {
            AuthResponse resp = googleAuthService.exchangeCodeForLogin(code);

            logger.info(
                    "Google callback successful for state={}, email={}",
                    state,
                    resp.getEmail()
            );

            // Return tokens and email as JSON body. Frontend should handle this response.
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            logger.error("Failed to exchange code for tokens", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper to percent-encode values used in fragment
    private static String encodeURIComponent(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Login using Google ID Token (for Google One Tap or GIS)
     */
    @PostMapping("/auth/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody GoogleRequest req) {
        AuthResponse resp = googleAuthService.loginWithGoogle(req);
        return ResponseEntity.ok(resp);
    }
}
