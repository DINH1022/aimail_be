package com.example.aimailbox.controller;

import com.example.aimailbox.dto.AuthRequest;
import com.example.aimailbox.dto.AuthResponse;
import com.example.aimailbox.dto.RefreshRequest;
import com.example.aimailbox.service.AuthService;
import com.example.aimailbox.service.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest req) {
        AuthResponse resp = authService.login(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest req) {
        AuthResponse resp = authService.register(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest req) {
        AuthResponse resp = authService.refreshToken(req.getRefreshToken());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshRequest req) {
        String token = req.getRefreshToken();
        refreshTokenService.deleteByToken(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof com.example.aimailbox.model.User)) {
            return ResponseEntity.status(401).build();
        }
        com.example.aimailbox.model.User user = (com.example.aimailbox.model.User) auth.getPrincipal();
        var body = java.util.Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "provider", user.getProvider()
        );
        return ResponseEntity.ok(body);
    }
}
