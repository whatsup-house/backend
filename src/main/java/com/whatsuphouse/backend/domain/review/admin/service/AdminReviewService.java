package com.whatsuphouse.backend.domain.review.admin.service;

import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeFeaturedRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.request.ReviewHomeOrderRequest;
import com.whatsuphouse.backend.domain.review.admin.dto.response.AdminReviewPageResponse;
import com.whatsuphouse.backend.domain.review.admin.dto.response.AdminReviewResponse;
import com.whatsuphouse.backend.domain.review.client.dto.response.ReviewDeleteResponse;
import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import com.whatsuphouse.backend.domain.review.repository.ReviewImageRepository;
import com.whatsuphouse.backend.domain.review.repository.ReviewRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
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

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;

    public AdminReviewPageResponse listReviews(Boolean homeFeatured, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<Review> reviewPage = homeFeatured == null
                ? reviewRepository.findByDeletedAtIsNull(pageable)
                : reviewRepository.findByIsHomeFeaturedAndDeletedAtIsNull(homeFeatured, pageable);

        Map<UUID, List<ReviewImage>> imageMap = findImageMap(reviewPage.getContent());
        List<AdminReviewResponse> content = reviewPage.getContent().stream()
                .map(review -> AdminReviewResponse.of(review, imageMap.getOrDefault(review.getId(), List.of())))
                .toList();

        return AdminReviewPageResponse.from(new PageImpl<>(content, pageable, reviewPage.getTotalElements()));
    }

    @Transactional
    public AdminReviewResponse updateHomeFeatured(UUID reviewId, ReviewHomeFeaturedRequest request) {
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        review.updateHomeFeatured(request.getIsHomeFeatured(), request.getHomeDisplayOrder());
        List<ReviewImage> images = reviewImageRepository.findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewId);
        return AdminReviewResponse.of(review, images);
    }

    @Transactional
    public void reorderHomeReviews(ReviewHomeOrderRequest request) {
        Map<UUID, Review> reviewMap = reviewRepository.findAllByIdInAndDeletedAtIsNull(
                        request.getItems().stream().map(item -> item.getReviewId()).toList())
                .stream()
                .collect(Collectors.toMap(Review::getId, review -> review));

        request.getItems().forEach(item -> {
            Review review = reviewMap.get(item.getReviewId());
            if (review == null) {
                throw new CustomException(ErrorCode.REVIEW_NOT_FOUND);
            }
            review.updateHomeFeatured(true, item.getHomeDisplayOrder());
        });
    }

    @Transactional
    public ReviewDeleteResponse deleteReview(UUID reviewId) {
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        review.delete();
        reviewImageRepository.findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(reviewId)
                .forEach(ReviewImage::delete);

        return ReviewDeleteResponse.of(review.getId());
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
