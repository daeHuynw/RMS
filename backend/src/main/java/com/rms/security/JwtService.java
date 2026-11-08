package com.rms.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.rms.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final SecurityProperties properties;
    private final byte[] secret;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        String value = properties.jwtSecret();
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("RMS_JWT_SECRET must be at least 32 bytes");
        }
        this.secret = value.getBytes(StandardCharsets.UTF_8);
    }

    public String createAccessToken(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expiresInSeconds());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(new SecretKeySpec(secret, "HmacSHA256")));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign access token", ex);
        }
        return jwt.serialize();
    }

    public long expiresInSeconds() {
        return properties.accessTokenMinutes() * 60L;
    }

    public Long parseUserId(String token) {
    try {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.HS256, new ImmutableSecret<>(secret)));
        JWTClaimsSet claims = processor.process(token, null);
        if (claims.getSubject() == null || claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
            throw new IllegalArgumentException("expired");
        }
        return Long.valueOf(claims.getSubject());
    } catch (Exception ex) {
        throw new IllegalArgumentException("invalid token");
    }
}
    
}