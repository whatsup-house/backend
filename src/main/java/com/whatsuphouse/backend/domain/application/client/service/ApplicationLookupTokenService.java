package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class ApplicationLookupTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public ApplicationLookupTokenService(@Value("${jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String createToken(String bookingNumber) {
        String payload = encode(bookingNumber.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payload);
        return payload + "." + signature;
    }

    public String extractBookingNumber(String token) {
        if (token == null || token.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        String expectedSignature = sign(parts[0]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return encode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign application lookup token", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
