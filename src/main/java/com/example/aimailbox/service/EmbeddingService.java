package com.example.aimailbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgvector.PGvector;
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

    public Mono<PGvector> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new PGvector(new float[768]));
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
                    float[] vector = new float[768];
                    if (valuesNode.isArray()) {
                        for (int i = 0; i < valuesNode.size(); i++) {
                            vector[i] = (float) valuesNode.get(i).asDouble();
                        }
                    }
                    return new PGvector(vector);
                })
                .onErrorResume(e -> {
                    log.error("Error generating embedding: {}", e.getMessage());
                    return Mono.just(new PGvector(new float[768])); // Fallback an toàn
                });
    }
}
