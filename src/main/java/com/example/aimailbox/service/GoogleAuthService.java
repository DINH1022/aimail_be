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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@Slf4j
public class GoogleAuthService {

    private static final String BASE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String SCOPE = "openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.compose https://mail.google.com/";

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


    public AuthResponse exchangeCodeForLogin(String authorizationCode) {
        try {
            // Exchange code for tokens
            GoogleTokenResponse tokenResponse = oAuthTokenService.exchangeCodeForTokens(authorizationCode);

            // Log scopes returned by token endpoint for debugging
            log.info("Token response scopes: {}", tokenResponse.getScope());

            // Ensure token has Gmail permissions we need
            String scopes = tokenResponse.getScope() == null ? "" : tokenResponse.getScope();
            boolean hasGmailScope = scopes.contains("gmail.") || scopes.contains("mail.google.com") || scopes.contains("gmail/readonly") || scopes.contains("gmail.send") || scopes.contains("gmail.modify") || scopes.contains("gmail.compose");
            if (!hasGmailScope) {
                log.warn("Access token missing Gmail scopes. Requested: {}", SCOPE);
                throw new RuntimeException("Access denied: Google did not grant Gmail permissions. Please reauthorize and approve Gmail scopes.");
            }

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
            
            // Find or create user (handle race condition on unique email)
            Optional<User> existingOpt = userRepository.findByEmail(email);
            User user;
            if (existingOpt.isPresent()) {
                user = existingOpt.get();
            } else {
                try {
                    User u = User.builder()
                            .email(email)
                            .provider("google")
                            .build();
                    user = userRepository.save(u);
                } catch (DataIntegrityViolationException dive) {
                    // Another request created the user concurrently. Refetch.
                    log.warn("Concurrent create detected for email {}, refetching user", email);
                    user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("Failed to create or find user after concurrent insert"));
                }
            }

            // Save OAuth tokens to user
            oAuthTokenService.saveTokensToUser(user, tokenResponse);

            // Generate JWT tokens (include oauth scopes from token response)
            String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), tokenResponse.getScope());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
            
            log.info("Successfully logged in user via Google OAuth: {}", email);
            return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());

        } catch (Exception e) {
            log.error("Failed to exchange code for login", e);
            throw new RuntimeException("OAuth login failed", e);
        }
    }

    public AuthResponse loginWithGoogle(GoogleRequest req) {
        // For security and to ensure the server can call Gmail, require the authorization-code flow
        // This lets the server obtain and persist Google access + refresh tokens. ID-token-only flow
        // does not provide a refresh token and cannot guarantee Gmail access.
        throw new RuntimeException("ID-token-only login not supported. Please use the authorization-code flow (exchange code on the server) to grant Gmail permissions.");
     }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
