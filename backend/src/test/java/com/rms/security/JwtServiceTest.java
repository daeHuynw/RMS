package com.rms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rms.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Hành vi của JwtService (REVIEW C4: bộ kiểm JWT dựng một lần và dùng chung cho mọi request,
 * nên phải đúng cả khi nhiều request kiểm token cùng lúc).
 */
class JwtServiceTest {

    private static final String SECRET = "test-only-secret-test-only-secret-0123";

    private final JwtService jwt = new JwtService(new SecurityProperties(SECRET, 30, 30));

    @Test
    void aTokenItIssuedIsAcceptedAndCarriesTheUserId() {
        assertThat(jwt.parseUserId(jwt.createAccessToken(42L, "a@example.com", List.of("CUSTOMER")))).isEqualTo(42L);
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = jwt.createAccessToken(42L, "a@example.com", List.of("CUSTOMER"));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThatThrownBy(() -> jwt.parseUserId(tampered)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() throws Exception {
        String foreign = sign("another-secret-another-secret-012345", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwt.parseUserId(foreign)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        String expired = sign(SECRET, Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> jwt.parseUserId(expired)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> jwt.parseUserId("not-a-jwt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manyRequestsCanCheckTokensAtTheSameTime() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (long id = 1; id <= 400; id++) {
                long userId = id;
                String token = jwt.createAccessToken(userId, "u" + userId + "@example.com", List.of("STAFF"));
                tasks.add(() -> jwt.parseUserId(token) == userId);
            }
            for (Future<Boolean> result : pool.invokeAll(tasks)) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String sign(String secret, Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .issueTime(Date.from(expiry.minusSeconds(1800)))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }
}
