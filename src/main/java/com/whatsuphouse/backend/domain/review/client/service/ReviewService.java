package com.whatsuphouse.backend.domain.review.client.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.review.client.dto.request.ReviewCreateRequest;
import com.whatsuphouse.backend.domain.review.client.dto.request.ReviewUpdateRequest;
import com.whatsuphouse.backend.domain.review.client.dto.response.HomeReviewResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewDeleteResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewLikeResponse;
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

import java.util.List;
import java.util.Map;
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
        if (!gatheringRepository.existsByIdAndDeletedAtIsNull(gatheringId)) {
            throw new CustomException(ErrorCode.GATHERING_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        Page<Review> reviewPage = reviewRepository.findByGatheringIdAndDeletedAtIsNull(gatheringId, pageable);
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

    public List<HomeReviewResponse> listHomeReviews() {
        List<Review> reviews = reviewRepository.findByIsHomeFeaturedTrueAndDeletedAtIsNullOrderByHomeDisplayOrderAscCreatedAtDesc();
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
