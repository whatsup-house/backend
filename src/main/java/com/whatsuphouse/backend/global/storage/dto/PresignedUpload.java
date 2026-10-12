package com.whatsuphouse.backend.global.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** 브라우저가 스토리지로 직접 PUT 할 presigned URL 과 업로드 후 공개 URL. */
@Getter
@AllArgsConstructor
public class PresignedUpload {

    private final String uploadUrl;
    private final String publicUrl;
    private final Instant expiresAt;
}
