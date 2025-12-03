package com.example.aimailbox.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key key;
    private final long accessExpirationMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-expiration-minutes}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpirationMillis = accessMinutes * 60 * 1000;
    }

    public String generateAccessToken(Long userId, String email, String oauthScopes) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessExpirationMillis);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("email", email)
                .claim("scope", oauthScopes)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Convenience overload used by callers that don't have OAuth scopes available
    public String generateAccessToken(Long userId, String email) {
        return generateAccessToken(userId, email, "");
    }

    public Jws<Claims> validateToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }
}
