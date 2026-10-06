package com.whatsuphouse.backend.domain.feed.admin.service;

import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedMediaRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostCreateRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostVisibilityRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.response.AdminFeedPostPageResponse;
import com.whatsuphouse.backend.domain.feed.admin.dto.response.AdminFeedPostResponse;
import com.whatsuphouse.backend.domain.feed.entity.FeedMedia;
import com.whatsuphouse.backend.domain.feed.entity.FeedPost;
import com.whatsuphouse.backend.domain.feed.enums.FeedMediaType;
import com.whatsuphouse.backend.domain.feed.repository.FeedPostRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/** 관리자 피드 게시물 CRUD. (KAN-380) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminFeedService {

    private static final String TEMP_PATH_PREFIX = "temp/";
    private static final String FEED_FOLDER = "feed";
    private static final String HTTPS_PREFIX = "https://";

    private final FeedPostRepository feedPostRepository;
    private final GatheringService gatheringService;
    private final StorageService storageService;

    // 비어 있으면(R2 연동 전) https URL 이기만 하면 허용한다. (KAN-379)
    @Value("${feed.video-base-url:}")
    private String videoBaseUrl;

    public AdminFeedPostPageResponse listFeedPosts(Boolean visible, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("id")));
        Page<FeedPost> posts = visible == null
                ? feedPostRepository.findByDeletedAtIsNull(pageable)
                : feedPostRepository.findByIsVisibleAndDeletedAtIsNull(visible, pageable);
        return AdminFeedPostPageResponse.from(posts.map(AdminFeedPostResponse::from));
    }

    @Transactional
    public AdminFeedPostResponse createFeedPost(FeedPostCreateRequest request) {
        Gathering gathering = findGathering(request.getGatheringId());
        FeedPost post = FeedPost.builder()
                .media(resolveMedia(request.getMedia()))
                .caption(request.getCaption())
                .instagramUrl(request.getInstagramUrl())
                .postedAt(request.getPostedAt())
                .gathering(gathering)
                .isVisible(Boolean.TRUE.equals(request.getVisible()))
                .build();
        return AdminFeedPostResponse.from(feedPostRepository.save(post));
    }

    @Transactional
    public AdminFeedPostResponse updateFeedPost(UUID id, FeedPostCreateRequest request) {
        FeedPost post = findFeedPost(id);
        Gathering gathering = findGathering(request.getGatheringId());
        post.update(resolveMedia(request.getMedia()), request.getCaption(), request.getInstagramUrl(),
                request.getPostedAt(), gathering);
        if (request.getVisible() != null) {
            post.changeVisibility(request.getVisible());
        }
        return AdminFeedPostResponse.from(post);
    }

    @Transactional
    public AdminFeedPostResponse changeFeedPostStatus(UUID id, FeedPostVisibilityRequest request) {
        FeedPost post = findFeedPost(id);
        post.changeVisibility(Boolean.TRUE.equals(request.getVisible()));
        return AdminFeedPostResponse.from(post);
    }

    @Transactional
    public void deleteFeedPost(UUID id) {
        findFeedPost(id).delete();
    }

    private FeedPost findFeedPost(UUID id) {
        return feedPostRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.FEED_POST_NOT_FOUND));
    }

    private Gathering findGathering(UUID gatheringId) {
        return gatheringId == null ? null : gatheringService.findGathering(gatheringId);
    }

    // 영상 URL 을 모두 검증한 뒤에 사진을 옮긴다(검증 실패 시 파일 이동이 일어나지 않게).
    // Storage move 는 트랜잭션 내부 호출이라 DB 실패 시 파일은 롤백되지 않는다(Gathering·Carousel 과 같은 trade-off).
    private List<FeedMedia> resolveMedia(List<FeedMediaRequest> media) {
        media.stream()
                .filter(item -> item.getType() == FeedMediaType.VIDEO)
                .forEach(item -> validateVideoUrl(item.getUrl()));
        return media.stream()
                .map(item -> new FeedMedia(
                        item.getType(),
                        item.getType() == FeedMediaType.VIDEO ? item.getUrl() : resolveImageUrl(item.getUrl()),
                        resolveImageUrl(item.getPosterUrl()),
                        item.getWidth(),
                        item.getHeight()))
                .toList();
    }

    // base-url 은 '/' 로 끝나는 형태로 비교한다 — "https://media.whatsup.house.evil.com/…" 같은 접두사 우회를 막는다.
    private void validateVideoUrl(String url) {
        String requiredPrefix = !StringUtils.hasText(videoBaseUrl) ? HTTPS_PREFIX
                : videoBaseUrl.endsWith("/") ? videoBaseUrl : videoBaseUrl + "/";
        if (!url.startsWith(requiredPrefix)) {
            throw new CustomException(ErrorCode.INVALID_FEED_VIDEO_URL);
        }
    }

    // AdminGatheringService.resolveImageUrls 와 같은 규칙: temp/ 경로만 정식 경로로 옮기고 나머지는 그대로 둔다.
    private String resolveImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return url.startsWith(TEMP_PATH_PREFIX) ? storageService.move(url, FEED_FOLDER) : url;
    }
}
