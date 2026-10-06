package com.whatsuphouse.backend.domain.feed.admin.service;

import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedMediaRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostCreateRequest;
import com.whatsuphouse.backend.domain.feed.admin.dto.request.FeedPostVisibilityRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminFeedServiceTest {

    private static final LocalDateTime POSTED_AT = LocalDateTime.of(2026, 10, 1, 19, 30);

    @Mock
    private FeedPostRepository feedPostRepository;

    @Mock
    private GatheringService gatheringService;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private AdminFeedService adminFeedService;

    private UUID postId;
    private FeedPost post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        post = FeedPost.builder()
                .media(List.of(FeedMedia.image("https://cdn.example.com/old.jpg")))
                .postedAt(POSTED_AT)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);
    }

    @Test
    @DisplayName("생성: visible 생략 시 숨김, temp 사진은 정식 경로로 이동, 게더링 연결")
    void createFeedPost_defaultsHidden_movesTempImages() {
        // given
        UUID gatheringId = UUID.randomUUID();
        Gathering gathering = Gathering.builder().title("와인 나잇").build();
        ReflectionTestUtils.setField(gathering, "id", gatheringId);
        given(gatheringService.findGathering(gatheringId)).willReturn(gathering);
        given(storageService.move("temp/feed/a.jpg", "feed")).willReturn("https://cdn.example.com/feed/a.jpg");
        given(feedPostRepository.save(any(FeedPost.class))).willAnswer(invocation -> invocation.getArgument(0));
        FeedPostCreateRequest request = request(gatheringId, null,
                image("temp/feed/a.jpg"), image("https://cdn.example.com/kept.jpg"));

        // when
        AdminFeedPostResponse result = adminFeedService.createFeedPost(request);

        // then
        assertThat(result.isVisible()).isFalse();
        assertThat(result.getMedia()).extracting(FeedMedia::getUrl)
                .containsExactly("https://cdn.example.com/feed/a.jpg", "https://cdn.example.com/kept.jpg");
        assertThat(result.getGathering().getId()).isEqualTo(gatheringId);
        assertThat(result.getPostedAt()).isEqualTo(POSTED_AT);
    }

    @Test
    @DisplayName("수정: media 순서 포함 전체 덮어쓰기")
    void updateFeedPost_overwritesMedia() {
        // given
        given(feedPostRepository.findByIdAndDeletedAtIsNull(postId)).willReturn(Optional.of(post));
        FeedPostCreateRequest request = request(null, true,
                image("https://cdn.example.com/2.jpg"), image("https://cdn.example.com/1.jpg"));

        // when
        AdminFeedPostResponse result = adminFeedService.updateFeedPost(postId, request);

        // then
        assertThat(result.getMedia()).extracting(FeedMedia::getUrl)
                .containsExactly("https://cdn.example.com/2.jpg", "https://cdn.example.com/1.jpg");
        assertThat(result.isVisible()).isTrue();
        assertThat(result.getGathering()).isNull();
    }

    @Test
    @DisplayName("노출 토글")
    void changeFeedPostStatus_togglesVisibility() {
        // given
        given(feedPostRepository.findByIdAndDeletedAtIsNull(postId)).willReturn(Optional.of(post));

        // when
        AdminFeedPostResponse shown = adminFeedService.changeFeedPostStatus(postId, new FeedPostVisibilityRequest(true));

        // then
        assertThat(shown.isVisible()).isTrue();
        assertThat(adminFeedService.changeFeedPostStatus(postId, new FeedPostVisibilityRequest(false)).isVisible()).isFalse();
    }

    @Test
    @DisplayName("삭제: soft delete")
    void deleteFeedPost_softDeletes() {
        // given
        given(feedPostRepository.findByIdAndDeletedAtIsNull(postId)).willReturn(Optional.of(post));

        // when
        adminFeedService.deleteFeedPost(postId);

        // then
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("없는 피드 게시물 → FEED_POST_NOT_FOUND")
    void deleteFeedPost_notFound_throws() {
        // given
        given(feedPostRepository.findByIdAndDeletedAtIsNull(postId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminFeedService.deleteFeedPost(postId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 게더링 → GATHERING_NOT_FOUND")
    void createFeedPost_gatheringNotFound_throws() {
        // given
        UUID gatheringId = UUID.randomUUID();
        given(gatheringService.findGathering(gatheringId)).willThrow(new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> adminFeedService.createFeedPost(request(gatheringId, null, image("https://cdn.example.com/a.jpg"))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GATHERING_NOT_FOUND);
    }

    @Test
    @DisplayName("영상 base-url 미설정: https 영상은 그대로 저장, poster 는 사진 규칙, http 는 거부")
    void createFeedPost_videoWithoutBaseUrl() {
        // given
        given(storageService.move("temp/feed/p.jpg", "feed")).willReturn("https://cdn.example.com/feed/p.jpg");
        given(feedPostRepository.save(any(FeedPost.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        AdminFeedPostResponse result = adminFeedService.createFeedPost(
                request(null, null, video("https://any.example.com/v.mp4", "temp/feed/p.jpg")));

        // then
        FeedMedia media = result.getMedia().get(0);
        assertThat(media.getType()).isEqualTo(FeedMediaType.VIDEO);
        assertThat(media.getUrl()).isEqualTo("https://any.example.com/v.mp4");
        assertThat(media.getPosterUrl()).isEqualTo("https://cdn.example.com/feed/p.jpg");
        assertThatThrownBy(() -> adminFeedService.createFeedPost(request(null, null, video("http://any.example.com/v.mp4", null))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FEED_VIDEO_URL);
    }

    @Test
    @DisplayName("영상 base-url 설정: 접두사가 다르면 거부되고 사진 이동도 일어나지 않음")
    void createFeedPost_videoWithBaseUrl() {
        // given
        ReflectionTestUtils.setField(adminFeedService, "videoBaseUrl", "https://media.whatsup.house/");
        given(feedPostRepository.save(any(FeedPost.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        AdminFeedPostResponse ok = adminFeedService.createFeedPost(
                request(null, null, video("https://media.whatsup.house/feed/v.mp4", null)));

        // then
        assertThat(ok.getMedia().get(0).getUrl()).isEqualTo("https://media.whatsup.house/feed/v.mp4");
        assertThatThrownBy(() -> adminFeedService.createFeedPost(
                request(null, null, image("temp/feed/a.jpg"), video("https://other.example.com/v.mp4", null))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FEED_VIDEO_URL);
        then(storageService).should(never()).move(anyString(), anyString());
    }

    @Test
    @DisplayName("영상 base-url 에 끝 슬래시가 없어도 호스트 접두사 우회를 거부한다")
    void createFeedPost_videoBaseUrlWithoutTrailingSlash_rejectsHostPrefixTrick() {
        // given
        ReflectionTestUtils.setField(adminFeedService, "videoBaseUrl", "https://media.whatsup.house");
        given(feedPostRepository.save(any(FeedPost.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        AdminFeedPostResponse ok = adminFeedService.createFeedPost(
                request(null, null, video("https://media.whatsup.house/feed/v.mp4", null)));

        // then
        assertThat(ok.getMedia().get(0).getUrl()).isEqualTo("https://media.whatsup.house/feed/v.mp4");
        assertThatThrownBy(() -> adminFeedService.createFeedPost(
                request(null, null, video("https://media.whatsup.house.evil.com/v.mp4", null))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FEED_VIDEO_URL);
    }

    private FeedPostCreateRequest request(UUID gatheringId, Boolean visible, FeedMediaRequest... media) {
        return FeedPostCreateRequest.builder()
                .media(List.of(media))
                .caption("캡션")
                .postedAt(POSTED_AT)
                .gatheringId(gatheringId)
                .visible(visible)
                .build();
    }

    private FeedMediaRequest image(String url) {
        return FeedMediaRequest.builder().type(FeedMediaType.IMAGE).url(url).build();
    }

    private FeedMediaRequest video(String url, String posterUrl) {
        return FeedMediaRequest.builder().type(FeedMediaType.VIDEO).url(url).posterUrl(posterUrl).build();
    }
}
