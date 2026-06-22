package com.whatsuphouse.backend.domain.translation.admin.dto.request;

import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTranslationOverrideRequest {

    @Schema(example = "GATHERING")
    @NotNull(message = "entityType은 필수입니다.")
    private TranslatableType entityType;

    @Schema(example = "c2000000-0000-0000-0000-000000000001")
    @NotNull(message = "entityId는 필수입니다.")
    private UUID entityId;

    @Schema(example = "title")
    @NotBlank(message = "field는 필수입니다.")
    @Size(max = 40, message = "field는 40자 이하여야 합니다.")
    private String field;

    @Schema(example = "EN", description = "로케일 (EN / JA)")
    @NotNull(message = "locale은 필수입니다.")
    private AppLocale locale;

    @Schema(example = "A casual gathering after work.")
    private String value;
}
