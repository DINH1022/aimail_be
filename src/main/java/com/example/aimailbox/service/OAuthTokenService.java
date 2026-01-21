package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.GoogleTokenResponse;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private final UserRepository userRepository;
    private final WebClient googleOauthClient;
    
    // Map to track ongoing refresh operations per user to avoid duplicate refresh calls
    private final Map<Long, Mono<GoogleTokenResponse>> refreshOperations = new ConcurrentHashMap<>();

    @Value("${google.client-id:}")
    private String clientId;

    @Value("${google.client-secret:}")
    private String clientSecret;

    @Value("${google.redirect-uri:}")
    private String redirectUri;

    public Mono<String> getValidAccessTokenReactive(User user) {
        return Mono.fromCallable(() -> userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found")))
                .flatMap(freshUser -> {
                    if (freshUser.getGoogleAccessToken() == null) {
                        log.error("User {} has no Google access token", freshUser.getEmail());
                        return Mono.error(new RuntimeException("No Google access token for user"));
                    }

                    // Check if token is expired or expiring soon (within 5 minutes)
                    if (freshUser.getGoogleTokenExpiryTime() != null &&
                            freshUser.getGoogleTokenExpiryTime().isBefore(Instant.now().plusSeconds(300))) {

                        if (freshUser.getGoogleRefreshToken() == null) {
                            log.error("User {} has expired token but no refresh token", freshUser.getEmail());
                            return Mono.error(new RuntimeException("Access token expired and no refresh token"));
                        }

                        log.info("Access token expiring soon for user {}, refreshing...", freshUser.getEmail());
                        
                        // Use computeIfAbsent to ensure only one refresh operation happens at a time per user
                        Mono<GoogleTokenResponse> refreshMono = refreshOperations.computeIfAbsent(
                            freshUser.getId(),
                            userId -> refreshAccessTokenReactive(freshUser.getGoogleRefreshToken())
                                .doOnNext(resp -> {
                                    // Reload user from DB to avoid stale state
                                    User userToUpdate = userRepository.findById(userId)
                                        .orElseThrow(() -> new RuntimeException("User not found during token save"));
                                    saveTokensToUser(userToUpdate, resp);
                                    log.info("Successfully refreshed and saved token for user {}", userToUpdate.getEmail());
                                })
                                .doFinally(signal -> {
                                    // Remove from map when done (success or error)
                                    refreshOperations.remove(userId);
                                    log.debug("Removed refresh operation from cache for user {}", userId);
                                })
                                .cache() // Cache result so multiple subscribers get the same result
                        );
                        
                        return refreshMono.map(GoogleTokenResponse::getAccessToken);
                    }

                    log.debug("Using existing valid token for user {}", freshUser.getEmail());
                    return Mono.just(freshUser.getGoogleAccessToken());
                });
    }

    public Mono<GoogleTokenResponse> refreshAccessTokenReactive(String refreshToken) {
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
                .onStatus(status -> status.isError(), resp ->
                        resp.bodyToMono(String.class)
                                .doOnNext(b -> log.error("Google refresh token error: {} - {}", resp.statusCode(), b))
                                .then(Mono.error(new RuntimeException("Failed to refresh access token: " + resp.statusCode()))))
                .bodyToMono(GoogleTokenResponse.class)
                .doOnSuccess(resp -> log.info("Successfully refreshed access token"))
                .doOnError(e -> log.error("Failed to refresh access token", e));
    }

    public void saveTokensToUser(User user, GoogleTokenResponse tokenResponse) {
        user.setGoogleAccessToken(tokenResponse.getAccessToken());
        if (tokenResponse.getRefreshToken() != null) {
            user.setGoogleRefreshToken(tokenResponse.getRefreshToken());
        }
        if (tokenResponse.getExpiresIn() != null) {
            user.setGoogleTokenExpiryTime(Instant.now().plusSeconds(tokenResponse.getExpiresIn()));
        }
        userRepository.save(user);
        log.info("Saved refreshed tokens for user {}", user.getEmail());
    }

    public GoogleTokenResponse exchangeCodeForTokens(String authorizationCode) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("code", authorizationCode);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", redirectUri);

        return googleOauthClient.post()
                .uri("/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.isError(), resp ->
                        resp.bodyToMono(String.class)
                                .doOnNext(b -> log.error("Google token exchange error: {} - {}", resp.statusCode(), b))
                                .then(Mono.error(new RuntimeException("Failed to exchange code: " + resp.statusCode()))))
                .bodyToMono(GoogleTokenResponse.class)
                .doOnSuccess(r -> log.info("Successfully exchanged code for tokens"))
                .doOnError(e -> log.error("Failed to exchange code for tokens", e))
                .block();
    }

    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);
        formData.add("grant_type", "refresh_token");

        return googleOauthClient.post()
                .uri("/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.isError(), resp ->
                        resp.bodyToMono(String.class)
                                .doOnNext(b -> log.error("Google refresh token error: {} - {}", resp.statusCode(), b))
                                .then(Mono.error(new RuntimeException("Failed to refresh access token: " + resp.statusCode()))))
                .bodyToMono(GoogleTokenResponse.class)
                .doOnSuccess(r -> log.info("Successfully refreshed access token"))
                .doOnError(e -> log.error("Failed to refresh access token", e))
                .block();
    }

    public String getValidAccessToken(User user) {
        if (user.getGoogleAccessToken() == null) {
            throw new RuntimeException("No Google access token available for user");
        }

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

    public boolean hasValidTokens(User user) {
        return user.getGoogleAccessToken() != null &&
                (user.getGoogleTokenExpiryTime() == null || user.getGoogleTokenExpiryTime().isAfter(Instant.now()));
    }

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

        user.setGoogleAccessToken(null);
        user.setGoogleRefreshToken(null);
        user.setGoogleTokenExpiryTime(null);
        userRepository.save(user);
    }

    public Optional<User> getUserWithValidTokens(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            try {
                getValidAccessToken(user);
                return Optional.of(userRepository.findById(userId).orElse(user));
            } catch (Exception e) {
                log.error("Failed to get valid tokens for user {}", userId, e);
                return userOpt;
            }
        }
        return Optional.empty();
    }
}
