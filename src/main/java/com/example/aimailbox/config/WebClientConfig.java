package com.example.aimailbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient gmailWebClient(WebClient.Builder builder){
        return builder.
                baseUrl("https://gmail.googleapis.com/gmail/v1/users/me")
                .build();
    }
}
