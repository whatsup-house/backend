package com.whatsuphouse.backend.global.storage.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.dto.PresignedUpload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2StorageServiceTest {

    @Test
    @DisplayName("설정이 하나라도 비면 비활성, 발급 시 503")
    void presignPut_missingConfig_disabled() {
        // given
        R2StorageService service = new R2StorageService("acc", "key", "secret", " ", "https://media.whatsup.house");

        // when & then
        assertThat(service.isEnabled()).isFalse();
        assertThatThrownBy(() -> service.presignPut("feed/videos/a.mp4", "video/mp4", 10, Duration.ofMinutes(10)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_VIDEO_UPLOAD_DISABLED);
    }

    @Test
    @DisplayName("presign 은 네트워크 호출 없이 path-style R2 URL 을 만들고 Content-Type·Content-Length 를 서명한다")
    void presignPut_buildsSignedUrlOffline() {
        // given — 존재하지 않는 계정·키. presign 이 네트워크를 탄다면 인증 실패나 DNS 오류로 예외가 난다.
        R2StorageService service = new R2StorageService(
                "no-such-account", "fake-key", "fake-secret", "bucket", "https://media.whatsup.house");

        // when
        PresignedUpload upload = service.presignPut("feed/videos/a.mp4", "video/mp4", 1234, Duration.ofMinutes(10));

        // then
        String url = URLDecoder.decode(upload.getUploadUrl(), StandardCharsets.UTF_8);
        assertThat(url)
                .startsWith("https://no-such-account.r2.cloudflarestorage.com/bucket/feed/videos/a.mp4?")
                .contains("X-Amz-Credential=fake-key/")
                .contains("/auto/s3/aws4_request")
                .contains("X-Amz-Expires=600");
        assertThat(url.replaceAll(".*X-Amz-SignedHeaders=([^&]*).*", "$1").split(";"))
                .contains("content-type", "content-length", "host");
        assertThat(upload.getPublicUrl()).isEqualTo("https://media.whatsup.house/feed/videos/a.mp4");
    }
}
