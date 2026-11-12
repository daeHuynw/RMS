package com.rms.modules.table.service;

import com.rms.modules.table.entity.DiningTable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Học tập: lưu raw token + SHA-256 hex hash (không AES).
 * Production nên mã hóa raw token at rest.
 */
@Service
public class QrTokenService {

    private final SecureRandom random = new SecureRandom();

    public String rotate(DiningTable table) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        table.setQrCredentials(raw, hash(raw));
        return raw;
    }

    public boolean matches(DiningTable table, String raw) {
        if (table.getQrTokenHash() == null || raw == null) {
            return false;
        }
        return MessageDigest.isEqual(
                table.getQrTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(raw).getBytes(StandardCharsets.US_ASCII));
    }

    public String requireRaw(DiningTable table) {
        if (table.getQrToken() == null) {
            return null;
        }
        return table.getQrToken();
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
