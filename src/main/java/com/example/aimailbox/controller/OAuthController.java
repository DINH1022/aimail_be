package com.example.aimailbox.controller;

import com.example.aimailbox.model.User;
import com.example.aimailbox.service.OAuthTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthTokenService oAuthTokenService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        try {
            User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Map<String, Object> status = new HashMap<>();
            status.put("email", user.getEmail());
            status.put("provider", user.getProvider());
            status.put("hasGoogleToken", user.getGoogleAccessToken() != null);
            status.put("hasRefreshToken", user.getGoogleRefreshToken() != null);
            
            if (user.getGoogleTokenExpiryTime() != null) {
                status.put("tokenExpiryTime", user.getGoogleTokenExpiryTime().toString());
                status.put("isTokenExpired", user.getGoogleTokenExpiryTime().isBefore(Instant.now()));
            }
            
            status.put("hasValidTokens", oAuthTokenService.hasValidTokens(user));
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get auth status", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refreshToken() {
        try {
            User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String accessToken = oAuthTokenService.getValidAccessToken(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Token refreshed successfully");
            response.put("hasValidToken", true);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/revoke")
    public ResponseEntity<Map<String, String>> revokeTokens() {
        try {
            User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            oAuthTokenService.revokeTokens(user);
            
            return ResponseEntity.ok(Map.of("message", "Tokens revoked successfully"));
        } catch (Exception e) {
            log.error("Failed to revoke tokens", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}