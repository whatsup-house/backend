package com.whatsuphouse.backend.global.storage.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.dto.PresignedUpload;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * Cloudflare R2(S3 호환) presigned PUT URL 발급. (KAN-379)
 * 설정 5개 중 하나라도 비어 있으면 비활성(isEnabled=false) — 서버는 정상 기동하고 발급 요청만 503.
 * presign 은 로컬 서명 계산이라 네트워크 호출이 없다.
 */
@Slf4j
@Service
public class R2StorageService {

    private static final String ENDPOINT_FORMAT = "https://%s.r2.cloudflarestorage.com";

    private final String bucket;
    private final String publicBaseUrl;
    private final S3Presigner presigner;

    public R2StorageService(@Value("${r2.account-id:}") String accountId,
                            @Value("${r2.access-key-id:}") String accessKeyId,
                            @Value("${r2.secret-access-key:}") String secretAccessKey,
                            @Value("${r2.bucket:}") String bucket,
                            @Value("${r2.public-base-url:}") String publicBaseUrl) {
        this.bucket = bucket.trim();
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl.trim());
        if (Stream.of(accountId, accessKeyId, secretAccessKey, bucket, publicBaseUrl).anyMatch(String::isBlank)) {
            log.info("[R2] 설정 미완료 — 영상 업로드 URL 발급 비활성");
            this.presigner = null;
            return;
        }
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(ENDPOINT_FORMAT.formatted(accountId.trim())))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    public boolean isEnabled() {
        return presigner != null;
    }

    /**
     * Content-Type·Content-Length 를 서명에 포함한 PUT URL 을 만든다 — 다른 형식·크기로 올리면 R2가 거부한다.
     * 활성(isEnabled) 상태에서만 호출한다.
     */
    public PresignedUpload presignPut(String key, String contentType, long contentLength, Duration ttl) {
        if (!isEnabled()) {
            throw new CustomException(ErrorCode.FEED_VIDEO_UPLOAD_DISABLED);
        }
        PresignedPutObjectRequest presigned = presigner.presignPutObject(builder -> builder
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build()));
        return new PresignedUpload(presigned.url().toString(), publicBaseUrl + "/" + key, presigned.expiration());
    }

    @PreDestroy
    void close() {
        if (presigner != null) {
            presigner.close();
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
