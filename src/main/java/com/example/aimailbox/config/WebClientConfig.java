package com.example.aimailbox.config;

import com.example.aimailbox.model.User;
import com.example.aimailbox.service.OAuthTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class WebClientConfig {

    @Bean
    public WebClient gmailWebClient(WebClient.Builder builder, OAuthTokenService oAuthTokenService) {
        return builder
                .baseUrl("https://gmail.googleapis.com/gmail/v1/users/me")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                        .build())
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

    /** Add Authorization header using servlet SecurityContextHolder (works in servlet MVC controllers) */
    @Bean
    public ExchangeFilterFunction authorizationHeaderFilter(OAuthTokenService oAuthTokenService) {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.fromCallable(() -> SecurityContextHolder.getContext())
                        .flatMap(ctx -> {
                            Authentication auth = ctx.getAuthentication();
                            if (auth != null && auth.getPrincipal() instanceof User user) {
                                return oAuthTokenService.getValidAccessTokenReactive(user)
                                        .map(token -> ClientRequest.from(request)
                                                .header("Authorization", "Bearer " + token)
                                                .build());
                            }
                            return Mono.just(request);
                        })
                        .defaultIfEmpty(request)
                        .onErrorResume(e -> {
                            log.error("Failed to add authorization header", e);
                            return Mono.just(request);
                        })
        );
    }

    /** Retry request once if 401 Unauthorized */
    private ExchangeFilterFunction tokenRefreshFilter(OAuthTokenService oAuthTokenService) {
        return (request, next) ->
                next.exchange(request)
                        .flatMap(response -> {
                            if (response.statusCode() != HttpStatus.UNAUTHORIZED) {
                                return Mono.just(response);
                            }
                            return Mono.fromCallable(() -> SecurityContextHolder.getContext())
                                    .flatMap(ctx -> {
                                        Authentication auth = ctx.getAuthentication();
                                        if (auth != null && auth.getPrincipal() instanceof User user) {
                                            log.warn("Received 401 for user {}, refreshing token", user.getEmail());
                                            return oAuthTokenService.getValidAccessTokenReactive(user)
                                                    .flatMap(newToken -> {
                                                        ClientRequest retryReq = ClientRequest.from(request)
                                                                .header("Authorization", "Bearer " + newToken)
                                                                .build();
                                                        log.info("Retrying request {} with refreshed token", request.url());
                                                        return next.exchange(retryReq)
                                                                .flatMap(resp -> {
                                                                    if (resp.statusCode().isError()) {
                                                                        return resp.bodyToMono(String.class)
                                                                                .defaultIfEmpty("")
                                                                                .doOnNext(body -> log.warn("Retry failed: status={} body={}", resp.statusCode(), body))
                                                                                .thenReturn(resp);
                                                                    }
                                                                    return Mono.just(resp);
                                                                });
                                                    })
                                                    .onErrorResume(e -> {
                                                        log.error("Token refresh or retry failed", e);
                                                        return Mono.just(response);
                                                    });
                                        }
                                        return Mono.just(response);
                                    })
                                    .defaultIfEmpty(response);
                        });
    }
}
