package com.whatsuphouse.backend.domain.translation.admin.dto.response;

import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminTranslationResponse {

    @Schema(description = "필드명", example = "title")
    private String field;

    @Schema(description = "로케일 코드", example = "en")
    private String locale;

    @Schema(description = "번역값", example = "A casual gathering after work.")
    private String value;

    @Schema(description = "번역 상태", example = "DONE")
    private TranslationStatus status;

    @Schema(description = "관리자 수동 보정 여부", example = "false")
    private boolean isOverride;

    public static AdminTranslationResponse from(ContentTranslation translation) {
        return AdminTranslationResponse.builder()
                .field(translation.getField())
                .locale(translation.getLocale())
                .value(translation.getValue())
                .status(translation.getStatus())
                .isOverride(translation.isOverride())
                .build();
    }
}
