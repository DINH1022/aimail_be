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

    /** Reactive method to get valid access token (refresh if expired) */
    public Mono<String> getValidAccessTokenReactive(User user) {
        if (user.getGoogleAccessToken() == null) {
            return Mono.error(new RuntimeException("No Google access token for user"));
        }

        if (user.getGoogleTokenExpiryTime() != null &&
                user.getGoogleTokenExpiryTime().isBefore(Instant.now().plusSeconds(300))) {

            if (user.getGoogleRefreshToken() == null) {
                return Mono.error(new RuntimeException("Access token expired and no refresh token"));
            }

            return refreshAccessTokenReactive(user.getGoogleRefreshToken())
                    .doOnNext(resp -> saveTokensToUser(user, resp))
                    .map(GoogleTokenResponse::getAccessToken);
        }

        return Mono.just(user.getGoogleAccessToken());
    }

    /** Reactive refresh token call */
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

    /** Save tokens to user */
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

    /**
     * Blocking exchange: authorization code -> tokens (used by GoogleAuthService)
     */
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

    /**
     * Blocking refresh used by non-reactive callers
     */
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

    /**
     * Get valid access token for user (blocking). Refreshes if expired.
     */
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

    /** Check if user has valid tokens */
    public boolean hasValidTokens(User user) {
        return user.getGoogleAccessToken() != null &&
                (user.getGoogleTokenExpiryTime() == null || user.getGoogleTokenExpiryTime().isAfter(Instant.now()));
    }

    /** Revoke tokens and clear DB */
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

    /**
     * Get user by ID and ensure tokens are refreshed if needed
     */
    public Optional<User> getUserWithValidTokens(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            try {
                getValidAccessToken(user); // may refresh and save
                return Optional.of(userRepository.findById(userId).orElse(user));
            } catch (Exception e) {
                log.error("Failed to get valid tokens for user {}", userId, e);
                return userOpt; // return user even if refresh failed
            }
        }
        return Optional.empty();
    }
}
