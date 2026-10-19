package com.whatsuphouse.backend.domain.gathering.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/** 심사용 숨김 종류 노출 규칙: 데모 회원 = 숨김만, 그 외(비회원 포함) = 숨김 제외, 관리자 = 전체. (KAN-407) */
@ExtendWith(MockitoExtension.class)
class GatheringServiceHiddenTest {

    @Mock
    private GatheringRepository gatheringRepository;
    @Mock
    private GatheringSessionRepository gatheringSessionRepository;
    @Mock
    private ApplicationService applicationService;
    @Mock
    private UserService userService;

    @InjectMocks
    private GatheringService gatheringService;

    private Gathering visibleKind;
    private Gathering hiddenKind;

    @BeforeEach
    void setUp() {
        visibleKind = kind("공개 모임", false);
        hiddenKind = kind("심사용 우연한 식탁", true);
        LocalDate date = LocalDate.now().plusDays(3);
        lenient().when(gatheringSessionRepository.findByEventDateGreaterThanEqualAndDeletedAtIsNull(LocalDate.now()))
                .thenReturn(List.of(session(visibleKind, date), session(hiddenKind, date)));
        lenient().when(gatheringRepository.findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc())
                .thenReturn(List.of(visibleKind, hiddenKind));
        lenient().when(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(
                        anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<UUID> ids = invocation.getArgument(0);
                    return List.of(visibleKind, hiddenKind).stream()
                            .filter(g -> ids.contains(g.getId()))
                            .map(g -> session(g, date)).toList();
                });
    }

    @Test
    @DisplayName("데모 회원은 숨김 종류만 본다")
    void list_demoUser_onlyHidden() {
        UserPrincipal demo = principal(false);
        given(userService.findUsersByIds(List.of(demo.getUserId())))
                .willReturn(Map.of(demo.getUserId(), user(demo.getUserId(), true)));

        List<GatheringResponse> result = gatheringService.listGatherings(null, null, demo);

        assertThat(result).extracting(GatheringResponse::getId).containsExactly(hiddenKind.getId());
    }

    @Test
    @DisplayName("비회원은 숨김 종류를 보지 못한다")
    void list_anonymous_excludesHidden() {
        List<GatheringResponse> result = gatheringService.listGatherings(null, null, null);

        assertThat(result).extracting(GatheringResponse::getId).containsExactly(visibleKind.getId());
    }

    @Test
    @DisplayName("일반 회원도 숨김 종류를 보지 못한다")
    void list_normalMember_excludesHidden() {
        UserPrincipal member = principal(false);
        given(userService.findUsersByIds(List.of(member.getUserId())))
                .willReturn(Map.of(member.getUserId(), user(member.getUserId(), false)));

        List<GatheringResponse> result = gatheringService.listGatherings(null, null, member);

        assertThat(result).extracting(GatheringResponse::getId).containsExactly(visibleKind.getId());
    }

    @Test
    @DisplayName("관리자는 전체를 본다")
    void list_admin_seesAll() {
        List<GatheringResponse> result = gatheringService.listGatherings(null, null, principal(true));

        assertThat(result).extracting(GatheringResponse::getId)
                .containsExactlyInAnyOrder(visibleKind.getId(), hiddenKind.getId());
    }

    @Test
    @DisplayName("홈 큐레이션도 같은 규칙이다")
    void curated_anonymous_excludesHidden() {
        List<CuratedGatheringResponse> result = gatheringService.listCuratedGatherings(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("공개 모임");
    }

    private static Gathering kind(String title, boolean hidden) {
        Gathering gathering = Gathering.builder().title(title).build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(gathering, "hidden", hidden);
        return gathering;
    }

    private static GatheringSession session(Gathering gathering, LocalDate date) {
        GatheringSession session = GatheringSession.builder().gathering(gathering).eventDate(date).maxAttendees(10).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private static UserPrincipal principal(boolean admin) {
        return new UserPrincipal(UUID.randomUUID(), "u@example.com", admin);
    }

    private static User user(UUID id, boolean demo) {
        User user = User.builder().email("u@example.com").password("pw").name("회원").nickname("닉").build();
        ReflectionTestUtils.setField(user, "id", id);
        if (demo) {
            user.markDemo();
        }
        return user;
    }
}
