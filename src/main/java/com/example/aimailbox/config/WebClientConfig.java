package com.example.aimailbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient gmailWebClient(WebClient.Builder builder) {
        // Increase buffer size to 50MB to handle large attachments
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(50 * 1024 * 1024)) // 50MB
                .build();

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(java.time.Duration.ofSeconds(60)); // 60 second timeout

        return builder
                .baseUrl("https://gmail.googleapis.com/gmail/v1/users/me")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
