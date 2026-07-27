package com.finance.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具(单用户自用,预留多用户)
 */
@Component
public class JwtUtil {
    // 生产环境应从 .env / 配置读取
    private static final String SECRET = "finance-system-secret-key-2026-charlie-graham-kiyosaki-x9k";
    private static final long EXPIRE = 7L * 24 * 60 * 60 * 1000; // 7天

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE))
                .signWith(key())
                .compact();
    }

    public String parseUsername(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
