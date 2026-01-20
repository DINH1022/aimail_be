package com.example.aimailbox.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class GeminiResponse {

    private List<Candidate> candidates;

    public String getText() {
        if (candidates == null || candidates.isEmpty()) return null;
        if (candidates.get(0).content == null) return null;
        if (candidates.get(0).content.parts == null || candidates.get(0).content.parts.isEmpty()) return null;

        return candidates.get(0).content.parts.get(0).text;
    }

    @Data
    public static class Candidate {
        private Content content;
    }

    @Data
    public static class Content {
        private List<Part> parts;
    }

    @Data
    public static class Part {
        private String text;
    }
}
