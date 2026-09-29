package com.whatsuphouse.backend.domain.review.admin.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeFeaturedRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeOrderItemRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeOrderRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.response.AdminReviewPageResponse;
import com.whatsuphouse.backend.domain.review.admin.dto.response.AdminReviewResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewDeleteResponse;
import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import com.whatsuphouse.backend.domain.review.repository.ReviewImageRepository;
import com.whatsuphouse.backend.domain.review.repository.ReviewRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @InjectMocks
    private AdminReviewService adminReviewService;

    private User user;
    private Gathering gathering;
    private Application application;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("reviewer@example.com")
                .password("encoded")
                .name("김리뷰")
                .gender(Gender.FEMALE)
                .age(27)
                .nickname("reviewer")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .eventDate(LocalDate.now().minusDays(1))
                .maxAttendees(10)
                .build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());

        application = Application.builder()
                .bookingNumber("WH260514-ABC123")
                .gathering(gathering)
                .user(user)
                .name(user.getName())
                .phone(user.getPhone())
                .build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("관리자 리뷰 목록 조회 성공")
    void listReviews_success() {
        Review review = buildReview(UUID.randomUUID(), "관리자 목록 리뷰입니다.");
        ReviewImage image = ReviewImage.builder()
                .review(review)
                .imageUrl("https://cdn.example.com/review/image.jpg")
                .displayOrder(0)
                .build();

        given(reviewRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(review)));
        given(reviewImageRepository.findByReviewIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(review.getId())))
                .willReturn(List.of(image));

        AdminReviewPageResponse response = adminReviewService.listReviews(null, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReviewContent()).isEqualTo("관리자 목록 리뷰입니다.");
        assertThat(response.getContent().get(0).getImages()).hasSize(1);
    }

    @Test
    @DisplayName("홈 노출 여부 필터로 관리자 리뷰 목록 조회 성공")
    void listReviews_homeFeaturedFilter_success() {
        Review review = buildReview(UUID.randomUUID(), "홈 노출 리뷰입니다.");
        review.updateHomeFeatured(true, 1);

        given(reviewRepository.findByIsHomeFeaturedAndDeletedAtIsNull(eq(true), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(review)));
        given(reviewImageRepository.findByReviewIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(review.getId())))
                .willReturn(List.of());

        AdminReviewPageResponse response = adminReviewService.listReviews(true, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).isHomeFeatured()).isTrue();
    }

    @Test
    @DisplayName("리뷰 홈 노출 설정 변경 성공")
    void updateHomeFeatured_success() {
        UUID reviewId = UUID.randomUUID();
        Review review = buildReview(reviewId, "홈 노출 설정할 리뷰입니다.");
        ReviewHomeFeaturedRequest request = ReviewHomeFeaturedRequest.builder()
                .isHomeFeatured(true)
                .homeDisplayOrder(3)
                .build();

        given(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).willReturn(Optional.of(review));
        given(reviewImageRepository.findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewId))
                .willReturn(List.of());

        AdminReviewResponse response = adminReviewService.updateHomeFeatured(reviewId, request);

        assertThat(response.isHomeFeatured()).isTrue();
        assertThat(response.getHomeDisplayOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 홈 노출 설정 변경 시 예외 발생")
    void updateHomeFeatured_reviewNotFound_throwsException() {
        UUID reviewId = UUID.randomUUID();
        ReviewHomeFeaturedRequest request = ReviewHomeFeaturedRequest.builder()
                .isHomeFeatured(true)
                .homeDisplayOrder(1)
                .build();
        given(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminReviewService.updateHomeFeatured(reviewId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("홈 노출 리뷰 순서 변경 성공")
    void reorderHomeReviews_success() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Review first = buildReview(firstId, "첫 번째 리뷰입니다.");
        Review second = buildReview(secondId, "두 번째 리뷰입니다.");
        ReviewHomeOrderRequest request = ReviewHomeOrderRequest.builder()
                .items(List.of(
                        ReviewHomeOrderItemRequest.builder().reviewId(firstId).homeDisplayOrder(1).build(),
                        ReviewHomeOrderItemRequest.builder().reviewId(secondId).homeDisplayOrder(2).build()))
                .build();

        given(reviewRepository.findAllByIdInAndDeletedAtIsNull(List.of(firstId, secondId)))
                .willReturn(List.of(first, second));

        adminReviewService.reorderHomeReviews(request);

        assertThat(first.isHomeFeatured()).isTrue();
        assertThat(first.getHomeDisplayOrder()).isEqualTo(1);
        assertThat(second.isHomeFeatured()).isTrue();
        assertThat(second.getHomeDisplayOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("홈 노출 리뷰 순서 변경 시 리뷰가 없으면 예외 발생")
    void reorderHomeReviews_reviewNotFound_throwsException() {
        UUID reviewId = UUID.randomUUID();
        ReviewHomeOrderRequest request = ReviewHomeOrderRequest.builder()
                .items(List.of(ReviewHomeOrderItemRequest.builder().reviewId(reviewId).homeDisplayOrder(1).build()))
                .build();
        given(reviewRepository.findAllByIdInAndDeletedAtIsNull(List.of(reviewId))).willReturn(List.of());

        assertThatThrownBy(() -> adminReviewService.reorderHomeReviews(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("관리자가 리뷰를 삭제하면 리뷰와 이미지를 soft delete 처리한다")
    void deleteReview_success() {
        UUID reviewId = UUID.randomUUID();
        Review review = buildReview(reviewId, "관리자가 삭제할 리뷰입니다.");
        ReviewImage image = ReviewImage.builder()
                .review(review)
                .imageUrl("https://cdn.example.com/review/delete.jpg")
                .displayOrder(0)
                .build();

        given(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).willReturn(Optional.of(review));
        given(reviewImageRepository.findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewId))
                .willReturn(List.of(image));

        ReviewDeleteResponse response = adminReviewService.deleteReview(reviewId);

        assertThat(response.getReviewId()).isEqualTo(reviewId);
        assertThat(response.isDeleted()).isTrue();
        assertThat(review.getDeletedAt()).isNotNull();
        assertThat(image.getDeletedAt()).isNotNull();
        verify(reviewImageRepository).findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewId);
    }

    @Test
    @DisplayName("관리자 리뷰 삭제 시 리뷰가 없으면 예외 발생")
    void deleteReview_reviewNotFound_throwsException() {
        UUID reviewId = UUID.randomUUID();
        given(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminReviewService.deleteReview(reviewId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_FOUND);
    }

    private Review buildReview(UUID reviewId, String content) {
        Review review = Review.builder()
                .user(user)
                .application(application)
                .gathering(gathering)
                .reviewType(ReviewType.TEXT)
                .reviewContent(content)
                .build();
        ReflectionTestUtils.setField(review, "id", reviewId);
        return review;
    }
}
