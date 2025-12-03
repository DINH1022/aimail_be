package com.example.aimailbox.config;

import com.example.aimailbox.model.User;
import com.example.aimailbox.service.OAuthTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class WebClientConfig {

    @Bean
    public WebClient gmailWebClient(WebClient.Builder builder, OAuthTokenService oAuthTokenService){
        return builder
                .baseUrl("https://gmail.googleapis.com/gmail/v1/users/me")
                .filter(authorizationHeaderFilter(oAuthTokenService))
                .filter(tokenRefreshFilter(oAuthTokenService))
                .build();
    }

    @Bean
    public WebClient googleOauthClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://oauth2.googleapis.com")
                .build();
    }

    /**
     * Exchange filter function to automatically refresh tokens on 401 responses
     */
    private ExchangeFilterFunction tokenRefreshFilter(OAuthTokenService oAuthTokenService) {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                // Token might be expired, try to refresh
                try {
                    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                    if (user != null) {
                        log.warn("Received 401 for user {}, attempting to refresh token", user.getEmail());
                        // Force refresh the token
                        String newToken = oAuthTokenService.getValidAccessToken(user);
                        log.info("Token refreshed successfully for user {}", user.getEmail());
                        
                        // You might want to retry the original request here with the new token
                        // For now, we'll just log and let the response flow through
                    }
                } catch (Exception e) {
                    log.error("Failed to refresh token on 401 response", e);
                }
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * Request filter to add authorization header automatically
     */
    @Bean
    public ExchangeFilterFunction authorizationHeaderFilter(OAuthTokenService oAuthTokenService) {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            try {
                User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (user != null) {
                    String accessToken = oAuthTokenService.getValidAccessToken(user);
                    ClientRequest authorizedRequest = ClientRequest.from(clientRequest)
                            .header("Authorization", "Bearer " + accessToken)
                            .build();
                    return Mono.just(authorizedRequest);
                }
            } catch (Exception e) {
                log.error("Failed to add authorization header", e);
            }
            return Mono.just(clientRequest);
        });
    }
}
