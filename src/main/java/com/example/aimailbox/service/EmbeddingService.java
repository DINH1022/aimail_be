package com.example.aimailbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {
    private final WebClient googleGenerativeClient;
    @Value("${google.generative-api-key}")
    private String apiKey;

    public Mono<float[]> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new float[0]);
        }

        String truncatedText = text.length() > 8000 ? text.substring(0, 8000) : text;
        String url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "model", "models/text-embedding-004",
                "content", Map.of("parts", List.of(Map.of("text", truncatedText)))
        );

        return googleGenerativeClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    JsonNode valuesNode = json.path("embedding").path("values");
                    if (valuesNode.isArray()) {
                        // Convert JSON Array sang float[]
                        float[] vector = new float[valuesNode.size()];
                        for (int i = 0; i < valuesNode.size(); i++) {
                            vector[i] = (float) valuesNode.get(i).asDouble();
                        }
                        return vector;
                    }
                    return new float[0];
                })
                .onErrorResume(e -> {
                    log.error("Error generating embedding: {}", e.getMessage());
                    return Mono.just(new float[0]);
                });
    }
}
