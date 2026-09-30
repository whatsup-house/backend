package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 채팅 본문 서버측 암호화. AES-256-GCM, 메시지마다 12바이트 랜덤 nonce, 128비트 태그, room_id를 AAD로 묶어
 * 다른 방으로 옮겨 붙인 암호문은 복호화되지 않는다.
 * 로그·예외에 평문과 키를 남기지 않는다(예외 메시지는 ErrorCode 고정 문구뿐).
 */
@Slf4j
@Component
public class ChatCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    // 운영은 CHAT_ENCRYPTION_KEY 필수(없거나 32바이트가 아니면 기동 실패). local/test는 yml의 고정 더미 키.
    public ChatCipher(@Value("${chat.encryption-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("chat.encryption-key는 base64 인코딩된 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public Encrypted encrypt(UUID roomId, String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            byte[] ciphertext = init(Cipher.ENCRYPT_MODE, roomId, nonce).doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Encrypted(ciphertext, nonce);
        } catch (GeneralSecurityException e) {
            log.error("[ChatCipher] 암호화 실패 roomId={} cause={}", roomId, e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.CHAT_CRYPTO_FAILED);
        }
    }

    public String decrypt(UUID roomId, byte[] ciphertext, byte[] nonce) {
        try {
            return new String(init(Cipher.DECRYPT_MODE, roomId, nonce).doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 태그 불일치(변조·다른 방 AAD·키 불일치) 포함
            log.error("[ChatCipher] 복호화 실패 roomId={} cause={}", roomId, e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.CHAT_CRYPTO_FAILED);
        }
    }

    private Cipher init(int mode, UUID roomId, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(roomId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    public record Encrypted(byte[] ciphertext, byte[] nonce) {
    }
}
