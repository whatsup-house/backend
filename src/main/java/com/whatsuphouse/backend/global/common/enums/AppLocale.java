package com.whatsuphouse.backend.global.common.enums;

import java.util.Arrays;
import java.util.Optional;

// 지원 로케일 (KAN-266). 기본 KO, 영어·일본어 번역 제공.
public enum AppLocale {
    KO("ko"),
    EN("en"),
    JA("ja");

    private final String code;

    AppLocale(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Optional<AppLocale> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(locale -> locale.code.equalsIgnoreCase(code)).findFirst();
    }

    // Accept-Language 헤더로 로케일을 결정한다. 미지원/누락 시 KO로 폴백. (예: "ja,en;q=0.8" → JA)
    public static AppLocale fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return KO;
        }
        String primary = header.split(",")[0].trim().split(";")[0].trim();
        String language = primary.split("-")[0].toLowerCase();
        return fromCode(language).orElse(KO);
    }
}
