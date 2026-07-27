package com.whatsuphouse.backend.global.storage.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseStorageServiceTest {

    private final SupabaseStorageService service = new SupabaseStorageService();

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0};
    private static final byte[] WEBP_MAGIC = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Test
    @DisplayName("PNG/JPEG/WEBP 매직바이트를 올바른 MediaType으로 판별한다")
    void detectsValidImageTypes() {
        assertThat(service.detectImageContentType(PNG_MAGIC)).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(service.detectImageContentType(JPEG_MAGIC)).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(service.detectImageContentType(WEBP_MAGIC)).isEqualTo(MediaType.parseMediaType("image/webp"));
    }

    @Test
    @DisplayName("WEBP로 위장한 비이미지 RIFF(WAVE 등)는 VP8 코덱 헤더가 없어 거부한다")
    void rejectsNonImageRiff() {
        byte[] wave = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
        assertThatThrownBy(() -> service.detectImageContentType(wave))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Test
    @DisplayName("이미지가 아닌 내용(매직바이트 불일치)은 INVALID_IMAGE_FORMAT으로 거부한다")
    void rejectsNonImageContent() {
        byte[] notImage = "<?php system($_GET[c]); ?>".getBytes();
        assertThatThrownBy(() -> service.detectImageContentType(notImage))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Test
    @DisplayName("허용되지 않은 folder는 INVALID_UPLOAD_FOLDER로 거부한다 (네트워크 호출 전)")
    void rejectsInvalidFolder() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_MAGIC);
        assertThatThrownBy(() -> service.upload(file, "../secret"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_UPLOAD_FOLDER);
    }

    @Test
    @DisplayName("png 확장자에 실제로는 이미지가 아닌 내용을 담으면 거부한다 (확장자 위조 방어)")
    void rejectsExtensionSpoofing() {
        MockMultipartFile spoof = new MockMultipartFile("file", "evil.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> service.upload(spoof, "avatar"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 INVALID_IMAGE_FORMAT으로 거부한다")
    void rejectsInvalidExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", PNG_MAGIC);
        assertThatThrownBy(() -> service.upload(file, "avatar"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FORMAT);
    }
}
