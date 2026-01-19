package com.example.aimailbox.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnoozeEmailRequest {
    
    @NotNull(message = "Snooze time is required")
    private Instant snoozeUntil;
    
    private String note;
    
    private String previousLabelId;
}
