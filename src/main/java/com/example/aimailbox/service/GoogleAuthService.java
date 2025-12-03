package com.example.aimailbox.service;

import com.example.aimailbox.dto.AuthResponse;
import com.example.aimailbox.dto.GoogleRequest;
import com.example.aimailbox.dto.GoogleTokenInfo;
import com.example.aimailbox.dto.GoogleTokenResponse;
import com.example.aimailbox.model.RefreshToken;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.UserRepository;
import com.example.aimailbox.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class GoogleAuthService {

    private static final String BASE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String SCOPE = "openid email profile";

    private final String clientId;
    private final String redirectUri;

    // dependencies for loginWithGoogle
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final WebClient googleOauthClient;
    private final OAuthTokenService oAuthTokenService;

    public GoogleAuthService(UserRepository userRepository,
                             RefreshTokenService refreshTokenService,
                             JwtUtil jwtUtil,
                             WebClient googleOauthClient,
                             OAuthTokenService oAuthTokenService,
                             @Value("${google.client-id:}") String clientId,
                             @Value("${google.redirect-uri:}") String redirectUri) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtil = jwtUtil;
        this.googleOauthClient = googleOauthClient;
        this.oAuthTokenService = oAuthTokenService;
        this.clientId = clientId == null ? "" : clientId;
        this.redirectUri = redirectUri == null ? "" : redirectUri;
    }

    public boolean isMisconfigured() {
        return clientId.isBlank() || redirectUri.isBlank();
    }

    public URI buildAuthorizationUri(String state) {
        if (isMisconfigured()) {
            throw new IllegalStateException("Google OAuth misconfigured");
        }
        StringBuilder sb = new StringBuilder(BASE).append("?")
                .append("client_id=").append(encode(clientId))
                .append("&redirect_uri=").append(encode(redirectUri))
                .append("&response_type=code")
                .append("&scope=").append(encode(SCOPE))
                .append("&access_type=offline")
                .append("&prompt=consent");
        if (state != null && !state.isBlank()) {
            sb.append("&state=").append(encode(state));
        }
        return URI.create(sb.toString());
    }

    /**
     * Exchange authorization code for tokens and login user
     */
    public AuthResponse exchangeCodeForLogin(String authorizationCode) {
        try {
            // Exchange code for tokens
            GoogleTokenResponse tokenResponse = oAuthTokenService.exchangeCodeForTokens(authorizationCode);
            
            // Get user info from ID token
            GoogleTokenInfo userInfo = googleOauthClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tokeninfo")
                            .queryParam("id_token", tokenResponse.getIdToken()).build())
                    .retrieve()
                    .bodyToMono(GoogleTokenInfo.class)
                    .block();

            if (userInfo == null || userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
                throw new RuntimeException("Invalid Google ID token");
            }

            // Validate audience if configured
            if (!clientId.isBlank() && userInfo.getAud() != null && !clientId.equals(userInfo.getAud())) {
                throw new RuntimeException("Google ID token audience does not match");
            }

            // Check email verification
            if (userInfo.getEmailVerified() != null && !userInfo.getEmailVerified().isEmpty()) {
                boolean verified = Boolean.parseBoolean(userInfo.getEmailVerified());
                if (!verified) {
                    throw new RuntimeException("Google account email is not verified");
                }
            }

            String email = userInfo.getEmail();
            
            // Find or create user
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User u = User.builder()
                        .email(email)
                        .provider("google")
                        .build();
                return userRepository.save(u);
            });

            // Save OAuth tokens to user
            oAuthTokenService.saveTokensToUser(user, tokenResponse);

            // Generate JWT tokens
            String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
            
            log.info("Successfully logged in user via Google OAuth: {}", email);
            return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());

        } catch (Exception e) {
            log.error("Failed to exchange code for login", e);
            throw new RuntimeException("OAuth login failed", e);
        }
    }

    public AuthResponse loginWithGoogle(GoogleRequest req) {
        String idToken = req.getIdToken();
        if (idToken == null || idToken.isBlank()) {
            throw new RuntimeException("Missing Google ID token");
        }
        // Call Google's tokeninfo endpoint to validate the ID token and extract the email
        GoogleTokenInfo info = googleOauthClient.get()
                .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                .retrieve()
                .bodyToMono(GoogleTokenInfo.class)
                .block();
        if (info == null || info.getEmail() == null || info.getEmail().isBlank()) {
            throw new RuntimeException("Invalid Google ID token");
        }
        // If a client ID is configured, validate the audience
        if (!clientId.isBlank() && info.getAud() != null && !clientId.equals(info.getAud())) {
            throw new RuntimeException("Google ID token audience does not match");
        }
        // If Google reports email_verified and it's false, reject
        if (info.getEmailVerified() != null && !info.getEmailVerified().isEmpty()) {
            boolean verified = Boolean.parseBoolean(info.getEmailVerified());
            if (!verified) {
                throw new RuntimeException("Google account email is not verified");
            }
        }

        String email = info.getEmail();
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = User.builder()
                    .email(email)
                    .provider("google")
                    .build();
            return userRepository.save(u);
        });
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
