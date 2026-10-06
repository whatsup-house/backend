package com.whatsuphouse.backend.domain.feed.client.service;

import com.whatsuphouse.backend.domain.feed.client.dto.response.FeedItemResponse;
import com.whatsuphouse.backend.domain.feed.client.dto.response.FeedResponse;
import com.whatsuphouse.backend.domain.feed.entity.FeedMedia;
import com.whatsuphouse.backend.domain.feed.entity.FeedPost;
import com.whatsuphouse.backend.domain.feed.repository.FeedPostRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.review.client.service.ReviewService;
import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Mock
    private FeedPostRepository feedPostRepository;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private FeedService feedService;

    @Test
    @DisplayName("게시물과 포토후기를 게시 시각 최신순으로 섞고, 남은 항목이 없으면 nextCursor 는 null")
    void listFeed_mergesNewestFirst_lastPageHasNullCursor() {
        // given
        FeedPost post1 = post(BASE.plusHours(3));
        FeedPost post2 = post(BASE.plusHours(1));
        Review review = review(BASE.plusHours(2));
        ReviewImage image = ReviewImage.builder().review(review).imageUrl("https://cdn.example.com/r.jpg").displayOrder(0).build();
        given(feedPostRepository.findVisibleBefore(any(), any(), any())).willReturn(List.of(post1, post2));
        given(reviewService.listPhotoReviewsBefore(any(), any(), anyInt())).willReturn(List.of(review));
        given(reviewService.findImageMap(List.of(review))).willReturn(Map.of(review.getId(), List.of(image)));

        // when
        FeedResponse result = feedService.listFeed(null, 10);

        // then
        assertThat(result.getItems()).extracting(FeedItemResponse::getId)
                .containsExactly(post1.getId(), review.getId(), post2.getId());
        FeedItemResponse reviewItem = result.getItems().get(1);
        assertThat(reviewItem.getKind()).isEqualTo(FeedItemResponse.Kind.REVIEW);
        assertThat(reviewItem.getReviewId()).isEqualTo(review.getId());
        assertThat(reviewItem.getMedia()).extracting(FeedMedia::getUrl).containsExactly("https://cdn.example.com/r.jpg");
        assertThat(reviewItem.getGathering().getTitle()).isEqualTo("와인 나잇");
        assertThat(result.getItems().get(0).getInstagramUrl()).isEqualTo("https://www.instagram.com/p/abc/");
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("size 를 넘으면 size 개만 주고 마지막 항목으로 커서를 만들며, 그 커서로 다음 페이지를 조회")
    void listFeed_overSize_returnsCursor_roundTrip() {
        // given
        FeedPost newest = post(BASE.plusHours(2));
        FeedPost middle = post(BASE.plusHours(1));
        FeedPost oldest = post(BASE);
        given(feedPostRepository.findVisibleBefore(any(), any(), any())).willReturn(List.of(newest, middle, oldest));
        given(reviewService.listPhotoReviewsBefore(any(), any(), anyInt())).willReturn(List.of());
        given(reviewService.findImageMap(List.of())).willReturn(Map.of());

        // when
        FeedResponse first = feedService.listFeed(null, 2);
        feedService.listFeed(first.getNextCursor(), 2);

        // then
        assertThat(first.getItems()).extracting(FeedItemResponse::getId).containsExactly(newest.getId(), middle.getId());
        assertThat(first.getNextCursor()).isNotNull().doesNotContain("=", "+", "/");
        then(feedPostRepository).should().findVisibleBefore(eq(middle.getPostedAt()), eq(middle.getId()), any());
        then(reviewService).should().listPhotoReviewsBefore(middle.getPostedAt(), middle.getId(), 3);
    }

    @Test
    @DisplayName("size 는 최대 30 으로 제한")
    void listFeed_sizeCappedAt30() {
        // given
        given(feedPostRepository.findVisibleBefore(any(), any(), any())).willReturn(List.of());
        given(reviewService.listPhotoReviewsBefore(any(), any(), anyInt())).willReturn(List.of());
        given(reviewService.findImageMap(List.of())).willReturn(Map.of());

        // when
        feedService.listFeed(null, 100);

        // then
        then(reviewService).should().listPhotoReviewsBefore(any(), any(), eq(31));
    }

    @Test
    @DisplayName("잘못된 커서 → INVALID_FEED_CURSOR")
    void listFeed_invalidCursor_throws() {
        // when & then
        assertThatThrownBy(() -> feedService.listFeed("not-a-cursor!!", 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FEED_CURSOR);
        assertThatThrownBy(() -> feedService.listFeed("aGVsbG8", 10))
                .isInstanceOf(CustomException.class);
    }

    private FeedPost post(LocalDateTime postedAt) {
        FeedPost post = FeedPost.builder()
                .media(List.of(FeedMedia.image("https://cdn.example.com/p.jpg")))
                .instagramUrl("https://www.instagram.com/p/abc/")
                .postedAt(postedAt)
                .isVisible(true)
                .build();
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        return post;
    }

    private Review review(LocalDateTime createdAt) {
        Gathering gathering = Gathering.builder().title("와인 나잇").build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        Review review = Review.builder().gathering(gathering).reviewType(ReviewType.PHOTO).reviewContent("좋았어요").build();
        ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(review, "createdAt", createdAt);
        return review;
    }
}
