package com.whatsuphouse.backend.domain.feed.repository;

import com.whatsuphouse.backend.domain.feed.entity.FeedMedia;
import com.whatsuphouse.backend.domain.feed.entity.FeedPost;
import com.whatsuphouse.backend.domain.feed.enums.FeedMediaType;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class FeedPostRepositoryTest {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 0, 0);
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Autowired
    private FeedPostRepository feedPostRepository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void setUp() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("공개 피드 조회는 노출 게시물만, 삭제 게시물 제외")
    void findVisibleBefore_excludesHiddenAndDeleted() {
        // given
        FeedPost visible = save(BASE, true);
        save(BASE.plusHours(1), false);
        FeedPost deleted = save(BASE.plusHours(2), true);
        deleted.delete();
        em.flush();
        em.clear();

        // when
        List<FeedPost> result = feedPostRepository.findVisibleBefore(FAR_FUTURE, new UUID(0, 0), PageRequest.of(0, 10));

        // then
        assertThat(result).extracting(FeedPost::getId).containsExactly(visible.getId());
    }

    @Test
    @DisplayName("keyset: 커서보다 이전 게시 시각 + 같은 시각이면 id 가 작은 것만, 최신순")
    void findVisibleBefore_appliesKeyset() {
        // given
        FeedPost older = save(BASE.minusHours(1), true);
        FeedPost sameA = save(BASE, true);
        FeedPost sameB = save(BASE, true);
        save(BASE.plusHours(1), true);
        em.flush();
        em.clear();
        FeedPost cursor = sameA.getId().toString().compareTo(sameB.getId().toString()) > 0 ? sameA : sameB;
        FeedPost sameSmaller = cursor == sameA ? sameB : sameA;

        // when
        List<FeedPost> result = feedPostRepository.findVisibleBefore(BASE, cursor.getId(), PageRequest.of(0, 10));

        // then
        assertThat(result).extracting(FeedPost::getId).containsExactly(sameSmaller.getId(), older.getId());
    }

    @Test
    @DisplayName("관리자 목록: visible 필터와 soft delete 제외")
    void findByIsVisibleAndDeletedAtIsNull_filters() {
        // given
        save(BASE, true);
        save(BASE, false);
        FeedPost deleted = save(BASE, false);
        deleted.delete();
        em.flush();
        em.clear();

        // when
        Page<FeedPost> hidden = feedPostRepository.findByIsVisibleAndDeletedAtIsNull(false, PageRequest.of(0, 10));
        Page<FeedPost> all = feedPostRepository.findByDeletedAtIsNull(PageRequest.of(0, 10));

        // then
        assertThat(hidden.getTotalElements()).isEqualTo(1);
        assertThat(all.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("jsonb media 는 순서·필드 그대로 왕복")
    void media_roundTrip() {
        // given
        FeedPost post = feedPostRepository.save(FeedPost.builder()
                .media(List.of(
                        new FeedMedia(FeedMediaType.VIDEO, "https://cdn.example.com/a.mp4", "https://cdn.example.com/a.jpg", 1080, 1920),
                        FeedMedia.image("https://cdn.example.com/b.jpg")))
                .postedAt(BASE)
                .build());
        em.flush();
        em.clear();

        // when
        FeedPost found = feedPostRepository.findByIdAndDeletedAtIsNull(post.getId()).orElseThrow();

        // then
        assertThat(found.getMedia()).hasSize(2);
        FeedMedia video = found.getMedia().get(0);
        assertThat(video.getType()).isEqualTo(FeedMediaType.VIDEO);
        assertThat(video.getUrl()).isEqualTo("https://cdn.example.com/a.mp4");
        assertThat(video.getPosterUrl()).isEqualTo("https://cdn.example.com/a.jpg");
        assertThat(video.getWidth()).isEqualTo(1080);
        assertThat(video.getHeight()).isEqualTo(1920);
        assertThat(found.getMedia().get(1).getType()).isEqualTo(FeedMediaType.IMAGE);
        assertThat(found.isVisible()).isFalse();
    }

    private FeedPost save(LocalDateTime postedAt, boolean visible) {
        return em.persist(FeedPost.builder()
                .media(List.of(FeedMedia.image("https://cdn.example.com/x.jpg")))
                .postedAt(postedAt)
                .isVisible(visible)
                .build());
    }
}
