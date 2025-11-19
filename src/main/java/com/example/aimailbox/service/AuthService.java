package com.example.aimailbox.service;

import com.example.aimailbox.dto.AuthRequest;
import com.example.aimailbox.dto.AuthResponse;
import com.example.aimailbox.dto.GoogleRequest;
import com.example.aimailbox.model.RefreshToken;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.UserRepository;
import com.example.aimailbox.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (user.getPassword() == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());
    }

    public AuthResponse register(AuthRequest req) {
        Optional<User> existing = userRepository.findByEmail(req.getEmail());
        if (existing.isPresent()) {
            throw new RuntimeException("Email already in use");
        }
        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .provider("local")
                .build();
        user = userRepository.save(user);
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());
    }

    public AuthResponse loginWithGoogle(GoogleRequest req) {
        String idToken = req.getIdToken();
        if (idToken == null || !idToken.contains("@")) {
            throw new RuntimeException("Invalid Google token (mock verification failed)");
        }
        User user = userRepository.findByEmail(idToken).orElseGet(() -> {
            User u = User.builder()
                    .email(idToken)
                    .provider("google")
                    .build();
            return userRepository.save(u);
        });
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());
    }

    public AuthResponse refreshToken(String refreshTokenStr) {
        RefreshToken newRefresh = refreshTokenService.rotateRefreshToken(refreshTokenStr);
        User user = newRefresh.getUser();
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, newRefresh.getToken(), user.getEmail());
    }
}
