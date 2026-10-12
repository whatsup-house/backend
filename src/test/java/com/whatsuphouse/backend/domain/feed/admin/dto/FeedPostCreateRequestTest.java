package com.whatsuphouse.backend.domain.feed.admin.dto;

import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedMediaRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostCreateRequest;
import com.whatsuphouse.backend.domain.feed.enums.FeedMediaType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeedPostCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("media 는 1~20개까지 허용 (인스타 게시물 최대 20장)")
    void media_sizeLimit() {
        // when
        Set<ConstraintViolation<FeedPostCreateRequest>> twenty = validator.validate(request(20));
        Set<ConstraintViolation<FeedPostCreateRequest>> twentyOne = validator.validate(request(21));
        Set<ConstraintViolation<FeedPostCreateRequest>> empty = validator.validate(request(0));

        // then
        assertThat(twenty).isEmpty();
        assertThat(twentyOne).extracting(v -> v.getPropertyPath().toString()).containsExactly("media");
        assertThat(empty).extracting(v -> v.getPropertyPath().toString()).containsExactly("media");
    }

    private FeedPostCreateRequest request(int mediaCount) {
        FeedMediaRequest image = FeedMediaRequest.builder().type(FeedMediaType.IMAGE).url("https://cdn.example.com/a.jpg").build();
        return FeedPostCreateRequest.builder()
                .media(Collections.nCopies(mediaCount, image))
                .postedAt(LocalDateTime.of(2026, 10, 1, 19, 30))
                .build();
    }
}
