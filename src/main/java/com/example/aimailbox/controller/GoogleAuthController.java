package com.example.aimailbox.controller;

import com.example.aimailbox.dto.response.AuthResponse;
import com.example.aimailbox.dto.request.GoogleRequest;
import com.example.aimailbox.service.GoogleAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Allow CORS from the frontend dev server and deployed frontend so the SPA can POST the code
@CrossOrigin(origins = {"https://aimail-7fc0kb7bb-vinhs-projects-373b8979.vercel.app", "http://localhost:5174", "http://localhost:5173", "http://localhost:3000","https://aimail-fsze5u387-vinhs-projects-373b8979.vercel.app/"}, allowedHeaders = "*", allowCredentials = "true",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RestController
public class GoogleAuthController {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthController.class);

    private final GoogleAuthService googleAuthService;

    // Frontend callback URL where the SPA will receive the code (default to deployed frontend)
    @Value("${frontend.callback-url:https://aimail-fsze5u387-vinhs-projects-373b8979.vercel.app/auth/google/callback}")
    private String frontendCallbackUrl;

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
     * Instead of exchanging code here, redirect to the frontend callback page so the SPA can
     * read the code and call the backend POST /auth/google/code to complete the exchange.
     */
    @GetMapping("/auth/google/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        if (googleAuthService.isMisconfigured()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        try {
            String target = frontendCallbackUrl + "?code=" + encodeURIComponent(code);
            if (state != null && !state.isBlank()) {
                target += "&state=" + encodeURIComponent(state);
            }
            URI uri = URI.create(target);
            logger.info("Redirecting backend callback to frontend: {}", uri);
            return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
        } catch (Exception e) {
            logger.error("Failed to redirect to frontend callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Frontend will POST the authorization code here after it receives it from Google redirect.
     * Expects JSON body: { "code": "..." }
     *
     * Endpoint: POST /auth/google/callback
     */
    @PostMapping("/auth/google/callback")
    public ResponseEntity<AuthResponse> exchangeCodeFromFrontend(@RequestBody Map<String, String> body) {
        if (googleAuthService.isMisconfigured()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String code = body != null ? body.get("code") : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            AuthResponse resp = googleAuthService.exchangeCodeForLogin(code);
            logger.info("Exchanged code from frontend for email={}", resp.getEmail());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Failed to exchange code from frontend", e);
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
