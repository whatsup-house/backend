package com.whatsuphouse.backend.domain.carousel.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class CarouselSlideCreateRequest extends CarouselSlideBaseRequest {

    @NotBlank
    @Override
    public String getTitle() {
        return super.getTitle();
    }

    @NotBlank
    @Override
    public String getTempPath() {
        return super.getTempPath();
    }
}
