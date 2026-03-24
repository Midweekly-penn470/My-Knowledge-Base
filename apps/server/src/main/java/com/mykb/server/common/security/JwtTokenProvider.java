package com.mykb.server.common.security;

import com.mykb.server.auth.entity.UserAccount;
import com.mykb.server.common.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final AppSecurityProperties properties;

  public JwtTokenProvider(AppSecurityProperties properties) {
    this.properties = properties;
    this.secretKey = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(UserAccount userAccount) {
    Instant now = Instant.now();
    Instant expiry = now.plus(properties.getTokenTtl());
    return Jwts.builder()
        .subject(userAccount.getId().toString())
        .claim("username", userAccount.getUsername())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey)
        .compact();
  }

  public Optional<AuthenticatedUser> parse(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
      return Optional.of(
          new AuthenticatedUser(
              UUID.fromString(claims.getSubject()), claims.get("username", String.class)));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }
}
