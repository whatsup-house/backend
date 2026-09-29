package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final ChatCipher cipher = new ChatCipher(KEY);
    private final UUID roomId = UUID.randomUUID();

    @Test
    @DisplayName("암호화한 본문은 같은 방(AAD)으로 복호화하면 원문이 나오고, 암호문에 평문이 남지 않는다")
    void encryptDecrypt_roundTrip() {
        // given
        String plaintext = "안녕하세요 👋 와썹하우스";

        // when
        ChatCipher.Encrypted encrypted = cipher.encrypt(roomId, plaintext);

        // then
        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(encrypted.ciphertext()).hasSize(plaintext.getBytes(StandardCharsets.UTF_8).length + 16); // 128비트 태그
        assertThat(new String(encrypted.ciphertext(), StandardCharsets.UTF_8)).doesNotContain("와썹하우스");
        assertThat(cipher.decrypt(roomId, encrypted.ciphertext(), encrypted.nonce())).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("같은 평문도 메시지마다 nonce가 달라 암호문이 다르다")
    void encrypt_randomNoncePerMessage() {
        // when
        ChatCipher.Encrypted first = cipher.encrypt(roomId, "같은 문장");
        ChatCipher.Encrypted second = cipher.encrypt(roomId, "같은 문장");

        // then
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    @DisplayName("다른 방 ID(AAD 불일치)로 복호화하면 실패한다")
    void decrypt_aadMismatch_fails() {
        // given
        ChatCipher.Encrypted encrypted = cipher.encrypt(roomId, "비밀 메시지");

        // when & then
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_CRYPTO_FAILED)
                .hasMessageNotContaining("비밀 메시지");
    }

    @Test
    @DisplayName("암호문이 변조되면 태그 검증으로 복호화가 실패한다")
    void decrypt_tampered_fails() {
        // given
        ChatCipher.Encrypted encrypted = cipher.encrypt(roomId, "원문");
        encrypted.ciphertext()[0] ^= 1;

        // when & then
        assertThatThrownBy(() -> cipher.decrypt(roomId, encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 기동 시점에 실패한다")
    void constructor_invalidKeyLength_fails() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new ChatCipher(shortKey))
                .isInstanceOf(IllegalStateException.class);
    }
}
