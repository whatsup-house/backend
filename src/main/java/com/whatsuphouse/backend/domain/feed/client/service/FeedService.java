package com.whatsuphouse.backend.domain.feed.client.service;

import com.whatsuphouse.backend.domain.feed.client.dto.response.FeedItemResponse;
import com.whatsuphouse.backend.domain.feed.client.dto.response.FeedResponse;
import com.whatsuphouse.backend.domain.feed.repository.FeedPostRepository;
import com.whatsuphouse.backend.domain.review.client.service.ReviewService;
import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 공개 피드. 노출 피드 게시물 + 포토후기를 (postedAt desc, id desc) 로 섞어 커서로 이어 준다. (KAN-380)
 * 두 소스를 각각 keyset 으로 size+1 개씩 가져와 메모리에서 병합한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FeedService {

    private static final int MAX_SIZE = 30;
    private static final String CURSOR_SEPARATOR = "|";
    // 첫 페이지: 어떤 게시 시각보다 늦은 값이라 keyset 의 postedAt < at 이 항상 참이다.
    private static final Cursor FIRST_PAGE = new Cursor(LocalDateTime.of(9999, 12, 31, 0, 0), new UUID(0, 0));
    // DB uuid 정렬(바이트 unsigned)과 같은 순서를 쓰려고 소문자 hex 문자열로 비교한다. UUID.compareTo 는 signed 라 다르다.
    private static final Comparator<FeedItemResponse> NEWEST_FIRST = Comparator
            .comparing(FeedItemResponse::getPostedAt)
            .thenComparing(item -> item.getId().toString())
            .reversed();

    private final FeedPostRepository feedPostRepository;
    private final ReviewService reviewService;

    public FeedResponse listFeed(String cursor, int size) {
        int limit = Math.min(Math.max(size, 1), MAX_SIZE);
        Cursor from = StringUtils.hasText(cursor) ? decode(cursor) : FIRST_PAGE;

        List<FeedItemResponse> items = new ArrayList<>();
        feedPostRepository.findVisibleBefore(from.at(), from.id(), PageRequest.of(0, limit + 1))
                .forEach(post -> items.add(FeedItemResponse.from(post)));
        List<Review> reviews = reviewService.listPhotoReviewsBefore(from.at(), from.id(), limit + 1);
        Map<UUID, List<ReviewImage>> imageMap = reviewService.findImageMap(reviews);
        reviews.forEach(review -> items.add(FeedItemResponse.of(review, imageMap.getOrDefault(review.getId(), List.of()))));
        items.sort(NEWEST_FIRST);

        if (items.size() <= limit) {
            return new FeedResponse(items, null);
        }
        List<FeedItemResponse> page = List.copyOf(items.subList(0, limit));
        FeedItemResponse last = page.get(limit - 1);
        return new FeedResponse(page, encode(new Cursor(last.getPostedAt(), last.getId())));
    }

    private record Cursor(LocalDateTime at, UUID id) {
    }

    private static String encode(Cursor cursor) {
        String raw = cursor.at() + CURSOR_SEPARATOR + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(CURSOR_SEPARATOR);
            return new Cursor(LocalDateTime.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException e) {
            throw new CustomException(ErrorCode.INVALID_FEED_CURSOR);
        }
    }
}
