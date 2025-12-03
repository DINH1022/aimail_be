package com.example.aimailbox.service;

import com.example.aimailbox.dto.GoogleTokenResponse;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private final UserRepository userRepository;
    private final WebClient googleOauthClient;

    @Value("${google.client-id:}")
    private String clientId;

    @Value("${google.client-secret:}")
    private String clientSecret;

    @Value("${google.redirect-uri:}")
    private String redirectUri;

    /**
     * Exchange authorization code for access and refresh tokens
     */
    public GoogleTokenResponse exchangeCodeForTokens(String authorizationCode) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("code", authorizationCode);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", redirectUri);

        log.info("Exchanging code for tokens with client_id: {}, redirect_uri: {}", clientId, redirectUri);

        return googleOauthClient.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    return response.bodyToMono(String.class)
                            .doOnNext(errorBody -> log.error("Google token exchange error: {} - {}", response.statusCode(), errorBody))
                            .then(Mono.error(new RuntimeException("Failed to exchange code: " + response.statusCode())));
                })
                .bodyToMono(GoogleTokenResponse.class)
                .doOnSuccess(response -> log.info("Successfully exchanged code for tokens"))
                .doOnError(error -> log.error("Failed to exchange code for tokens", error))
                .block();
    }

    /**
     * Refresh access token using refresh token
     */
    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);
        formData.add("grant_type", "refresh_token");

        return googleOauthClient.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(GoogleTokenResponse.class)
                .doOnSuccess(response -> log.info("Successfully refreshed access token"))
                .doOnError(error -> log.error("Failed to refresh access token", error))
                .block();
    }

    /**
     * Save OAuth tokens to user
     */
    public void saveTokensToUser(User user, GoogleTokenResponse tokenResponse) {
        user.setGoogleAccessToken(tokenResponse.getAccessToken());
        if (tokenResponse.getRefreshToken() != null) {
            user.setGoogleRefreshToken(tokenResponse.getRefreshToken());
        }
        // Calculate expiry time (expires_in is in seconds)
        if (tokenResponse.getExpiresIn() != null) {
            user.setGoogleTokenExpiryTime(Instant.now().plusSeconds(tokenResponse.getExpiresIn()));
        }
        userRepository.save(user);
        log.info("Saved OAuth tokens for user: {}", user.getEmail());
    }

    /**
     * Get valid access token for user (refresh if expired)
     */
    public String getValidAccessToken(User user) {
        if (user.getGoogleAccessToken() == null) {
            throw new RuntimeException("No Google access token available for user");
        }

        // Check if token is expired (with 5 minute buffer)
        if (user.getGoogleTokenExpiryTime() != null && 
            user.getGoogleTokenExpiryTime().isBefore(Instant.now().plusSeconds(300))) {
            
            if (user.getGoogleRefreshToken() == null) {
                throw new RuntimeException("Access token expired and no refresh token available");
            }

            try {
                GoogleTokenResponse refreshedTokens = refreshAccessToken(user.getGoogleRefreshToken());
                saveTokensToUser(user, refreshedTokens);
                return refreshedTokens.getAccessToken();
            } catch (Exception e) {
                log.error("Failed to refresh token for user {}", user.getEmail(), e);
                throw new RuntimeException("Failed to refresh access token", e);
            }
        }

        return user.getGoogleAccessToken();
    }

    /**
     * Check if user has valid OAuth tokens
     */
    public boolean hasValidTokens(User user) {
        return user.getGoogleAccessToken() != null && 
               (user.getGoogleTokenExpiryTime() == null || 
                user.getGoogleTokenExpiryTime().isAfter(Instant.now()));
    }

    /**
     * Revoke OAuth tokens for user
     */
    public void revokeTokens(User user) {
        if (user.getGoogleAccessToken() != null) {
            try {
                googleOauthClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("oauth2.googleapis.com")
                                .path("/revoke")
                                .queryParam("token", user.getGoogleAccessToken())
                                .build())
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
                
                log.info("Successfully revoked tokens for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to revoke tokens for user: {}", user.getEmail(), e);
            }
        }

        // Clear tokens from database
        user.setGoogleAccessToken(null);
        user.setGoogleRefreshToken(null);
        user.setGoogleTokenExpiryTime(null);
        userRepository.save(user);
    }

    /**
     * Get user by ID and refresh token if needed
     */
    public Optional<User> getUserWithValidTokens(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            try {
                getValidAccessToken(user); // This will refresh if needed
                return Optional.of(userRepository.findById(userId).orElse(user)); // Refetch to get updated tokens
            } catch (Exception e) {
                log.error("Failed to get valid tokens for user {}", userId, e);
                return userOpt; // Return user even if token refresh fails
            }
        }
        return Optional.empty();
    }
}