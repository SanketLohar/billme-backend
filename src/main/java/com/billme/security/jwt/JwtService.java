package com.billme.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // 🔐 Change this secret in production
    private static final String SECRET =
            "my_super_secret_key_for_billme_application_very_secure_123456";

    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // Token durations
    private static final long ADMIN_EXPIRATION = 1000 * 60 * 15;  // 15 minutes
    private static final long USER_EXPIRATION  = 1000 * 60 * 60;  // 1 hour

    // ✅ Generate Access Token
    public String generateAccessToken(String username, String role) {

        long expirationTime;

        if ("ADMIN".equals(role)) {
            expirationTime = ADMIN_EXPIRATION;
        } else {
            expirationTime = USER_EXPIRATION;
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // ✅ Extract username
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    // ✅ Extract role
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // ✅ Extract all claims
    public Claims extractAllClaims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ✅ Validate token
    public boolean isTokenValid(String token) {

        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}