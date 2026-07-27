package com.whatsuphouse.backend.domain.review.client.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.domain.review.client.dto.request.ReviewCreateRequest;
import com.whatsuphouse.backend.domain.review.client.dto.request.ReviewUpdateRequest;
import com.whatsuphouse.backend.domain.review.client.dto.response.HomeReviewResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewDeleteResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewLikeResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewLocateResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewPageResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewResponse;
import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import com.whatsuphouse.backend.domain.review.entity.ReviewLike;
import com.whatsuphouse.backend.domain.review.enums.ReviewSort;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import com.whatsuphouse.backend.domain.review.repository.ReviewImageRepository;
import com.whatsuphouse.backend.domain.review.repository.ReviewLikeRepository;
import com.whatsuphouse.backend.domain.review.repository.ReviewRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewService {


    private final ApplicationRepository applicationRepository;
    private final GatheringRepository gatheringRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final StorageService storageService;
    private final MileageService mileageService;
    private final UserNotificationService userNotificationService;

    // 후기 좋아요 마일스톤 — 이 값에 도달하면 작성자에게 알림. (KAN-263)
    private static final Set<Integer> LIKE_MILESTONES = Set.of(10, 50, 100, 500, 1000);

    // 관리자 홈 노출 후기가 없을 때 추천순으로 채우는 fallback 개수. (KAN-294)
    private static final int HOME_REVIEW_FALLBACK_SIZE = 6;

    @Transactional
    public ReviewResponse createReview(ReviewCreateRequest request, UUID userId) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(request.getApplicationId())
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        validateWritableApplication(application, userId);

        if (reviewRepository.existsByApplicationIdAndDeletedAtIsNull(application.getId())) {
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        boolean hasImages = request.getImageTempPaths() != null && !request.getImageTempPaths().isEmpty();
        Review review = Review.builder()
                .user(application.getUser())
                .application(application)
                .gathering(application.getGathering())
                .reviewType(hasImages ? ReviewType.PHOTO : ReviewType.TEXT)
                .reviewContent(request.getReviewContent())
                .build();

        Review savedReview = reviewRepository.save(review);
        List<ReviewImage> images = saveImages(savedReview, request.getImageTempPaths());
        mileageService.rewardReview(application.getUser(), savedReview.getId(), savedReview.getReviewType());

        return ReviewResponse.of(savedReview, images);
    }

    @Transactional
    public ReviewResponse updateReview(UUID reviewId, ReviewUpdateRequest request, UUID userId) {
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.REVIEW_APPLICATION_FORBIDDEN);
        }

        ReviewType previousType = review.getReviewType();
        List<ReviewImage> images = updateImagesIfRequested(review, request.getImageTempPaths());
        ReviewType updatedType = images.isEmpty() ? ReviewType.TEXT : ReviewType.PHOTO;
        review.update(request.getReviewContent(), updatedType);

        if (previousType == ReviewType.TEXT && updatedType == ReviewType.PHOTO) {
            mileageService.rewardReviewUpgradeIfAbsent(review.getUser(), review.getId());
        }

        return ReviewResponse.of(review, images);
    }

    @Transactional
    public ReviewLikeResponse toggleLike(UUID reviewId, UUID userId) {
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId)
                .map(reviewLike -> unlike(review, reviewLike))
                .orElseGet(() -> like(review, user));
    }

    @Transactional
    public ReviewDeleteResponse deleteReview(UUID reviewId, UUID userId) {
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.REVIEW_APPLICATION_FORBIDDEN);
        }

        review.delete();
        reviewImageRepository.findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewId)
                .forEach(ReviewImage::delete);

        return ReviewDeleteResponse.of(review.getId());
    }

    public ReviewPageResponse getGatheringReviews(UUID gatheringId, ReviewSort sort, int page, int size) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        Page<Review> reviewPage = reviewRepository.findByGatheringReviewGroupAndDeletedAtIsNull(
                gathering.getTitle(), gathering.getGatheringType(), pageable);
        return toReviewPageResponse(reviewPage, pageable);
    }

    public ReviewPageResponse getReviews(ReviewSort sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        Page<Review> reviewPage = reviewRepository.findByDeletedAtIsNull(pageable);
        return toReviewPageResponse(reviewPage, pageable);
    }

    public ReviewPageResponse getMyReviews(UUID userId, ReviewSort sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        Page<Review> reviewPage = reviewRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        return toReviewPageResponse(reviewPage, pageable);
    }

    public ReviewLocateResponse locateReview(UUID reviewId, ReviewSort sort, UUID gatheringId, int size) {
        int effectiveSize = size > 0 ? size : 10;

        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (gatheringId != null) {
            Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                    .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
            if (!isSameReviewGroup(review.getGathering(), gathering)) {
                throw new CustomException(ErrorCode.REVIEW_NOT_FOUND);
            }
        }

        long precedingCount = countPrecedingReviews(review, sort, gatheringId);
        int page = (int) (precedingCount / effectiveSize);
        return ReviewLocateResponse.of(page, effectiveSize, sort);
    }

    public List<HomeReviewResponse> listHomeReviews() {
        List<Review> reviews = reviewRepository.findByIsHomeFeaturedTrueAndDeletedAtIsNullOrderByHomeDisplayOrderAscCreatedAtDesc();

        // 관리자가 홈 노출 후기를 설정하지 않았으면 추천순(좋아요 → 최신) 후기로 fallback 한다. (KAN-294)
        if (reviews.isEmpty()) {
            Pageable pageable = PageRequest.of(0, HOME_REVIEW_FALLBACK_SIZE,
                    Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt")));
            reviews = reviewRepository.findByDeletedAtIsNull(pageable).getContent();
        }

        Map<UUID, List<ReviewImage>> imageMap = findImageMap(reviews);

        return reviews.stream()
                .map(review -> HomeReviewResponse.of(review, imageMap.getOrDefault(review.getId(), List.of())))
                .toList();
    }

    private ReviewPageResponse toReviewPageResponse(Page<Review> reviewPage, Pageable pageable) {
        Map<UUID, List<ReviewImage>> imageMap = findImageMap(reviewPage.getContent());

        List<ReviewResponse> content = reviewPage.getContent().stream()
                .map(review -> ReviewResponse.of(review, imageMap.getOrDefault(review.getId(), List.of())))
                .toList();

        return ReviewPageResponse.from(new PageImpl<>(content, pageable, reviewPage.getTotalElements()));
    }

    private ReviewLikeResponse like(Review review, User user) {
        reviewLikeRepository.save(ReviewLike.builder()
                .review(review)
                .user(user)
                .build());
        review.increaseLikeCount();

        // 좋아요 수가 새 마일스톤에 도달하면 후기 작성자에게 알림을 발행한다. (KAN-263)
        // 이미 발행한 마일스톤보다 클 때만 발행해, unlike→relike로 같은 값을 재교차해도 중복 발행하지 않는다.
        int likeCount = review.getLikeCount();
        if (LIKE_MILESTONES.contains(likeCount) && likeCount > review.getNotifiedLikeMilestone()) {
            review.markLikeMilestoneNotified(likeCount);
            userNotificationService.create(
                    review.getUser(),
                    NotificationType.REVIEW_LIKE_MILESTONE,
                    "후기 좋아요 " + likeCount + "개 달성!",
                    "작성하신 후기가 좋아요 " + likeCount + "개를 받았어요.",
                    NotificationLink.REVIEWS);
        }

        return ReviewLikeResponse.of(review.getId(), true, review.getLikeCount());
    }

    private ReviewLikeResponse unlike(Review review, ReviewLike reviewLike) {
        reviewLikeRepository.delete(reviewLike);
        review.decreaseLikeCount();
        return ReviewLikeResponse.of(review.getId(), false, review.getLikeCount());
    }

    private void validateWritableApplication(Application application, UUID userId) {
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.REVIEW_APPLICATION_FORBIDDEN);
        }

        if (application.getStatus() != ApplicationStatus.ATTENDED) {
            throw new CustomException(ErrorCode.REVIEW_APPLICATION_NOT_ATTENDED);
        }
    }

    private List<ReviewImage> saveImages(Review review, List<String> imageTempPaths) {
        if (imageTempPaths == null || imageTempPaths.isEmpty()) {
            return List.of();
        }

        List<ReviewImage> images = IntStream.range(0, imageTempPaths.size())
                .mapToObj(index -> {
                    String imageUrl = storageService.move(imageTempPaths.get(index), "review");
                    return ReviewImage.builder()
                            .review(review)
                            .imageUrl(imageUrl)
                            .displayOrder(index)
                            .build();
                })
                .toList();

        return reviewImageRepository.saveAll(images);
    }

    private List<ReviewImage> updateImagesIfRequested(Review review, List<String> imageTempPaths) {
        List<ReviewImage> existingImages = reviewImageRepository
                .findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(review.getId());

        if (imageTempPaths == null) {
            return existingImages;
        }

        existingImages.forEach(ReviewImage::delete);
        return saveImages(review, imageTempPaths);
    }

    private Sort toSort(ReviewSort sort) {
        if (sort == ReviewSort.LIKES) {
            return Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
        }
        return Sort.by(Sort.Order.desc("createdAt"));
    }

    // 목록 정렬 기준에서 대상 리뷰보다 앞서는 리뷰 수를 센다. (toSort 정렬과 동일한 규칙)
    private long countPrecedingReviews(Review review, ReviewSort sort, UUID gatheringId) {
        LocalDateTime createdAt = review.getCreatedAt();
        Integer likeCount = review.getLikeCount();

        if (gatheringId != null) {
            if (sort == ReviewSort.LIKES) {
                Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                        .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
                return reviewRepository.countByGatheringReviewGroupAndDeletedAtIsNullAndLikeCountGreaterThan(
                        gathering.getTitle(), gathering.getGatheringType(), likeCount)
                        + reviewRepository.countByGatheringReviewGroupAndDeletedAtIsNullAndLikeCountAndCreatedAtAfter(
                        gathering.getTitle(), gathering.getGatheringType(), likeCount, createdAt);
            }
            Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                    .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
            return reviewRepository.countByGatheringReviewGroupAndDeletedAtIsNullAndCreatedAtAfter(
                    gathering.getTitle(), gathering.getGatheringType(), createdAt);
        }

        if (sort == ReviewSort.LIKES) {
            return reviewRepository.countByDeletedAtIsNullAndLikeCountGreaterThan(likeCount)
                    + reviewRepository.countByDeletedAtIsNullAndLikeCountAndCreatedAtAfter(likeCount, createdAt);
        }
        return reviewRepository.countByDeletedAtIsNullAndCreatedAtAfter(createdAt);
    }

    private boolean isSameReviewGroup(Gathering left, Gathering right) {
        return left.getTitle().equals(right.getTitle())
                && left.getGatheringType() == right.getGatheringType();
    }

    private Map<UUID, List<ReviewImage>> findImageMap(List<Review> reviews) {
        List<UUID> reviewIds = reviews.stream()
                .map(Review::getId)
                .toList();

        if (reviewIds.isEmpty()) {
            return Map.of();
        }

        return reviewImageRepository.findByReviewIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewIds)
                .stream()
                .collect(Collectors.groupingBy(image -> image.getReview().getId()));
    }
}
