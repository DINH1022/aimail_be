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

    @GetMapping("/auth/google/authorize")
    public ResponseEntity<Void> authorize(@RequestParam(required = false) String state) {
        if (googleAuthService.isMisconfigured()) {
            // Misconfiguration: return 500 so frontend can show error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        URI uri = googleAuthService.buildAuthorizationUri(state);
        // Log the URI to the console for easier testing
        logger.info("Google authorization URI: {}", uri.toString());
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }

    @PostMapping("/auth/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody GoogleRequest req) {
        AuthResponse resp = googleAuthService.loginWithGoogle(req);
        return ResponseEntity.ok(resp);
    }
}
