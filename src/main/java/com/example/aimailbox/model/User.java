package com.example.aimailbox.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    private String provider;

    // OAuth tokens for Google
    private String googleAccessToken;
    
    private String googleRefreshToken;
    
    private Instant googleTokenExpiryTime;
}

