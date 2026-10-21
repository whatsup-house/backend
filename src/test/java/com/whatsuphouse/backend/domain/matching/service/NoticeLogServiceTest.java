package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.dto.response.NoticeLogResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.NoticeLog;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.enums.NoticeResult;
import com.whatsuphouse.backend.domain.matching.enums.TableNoticeStatus;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.NoticeLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class NoticeLogServiceTest {

    @Mock
    private NoticeLogRepository noticeLogRepository;
    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private GatheringService gatheringService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private NoticeLogService noticeLogService;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID tableId = UUID.randomUUID();

    private NoticeLog log(UUID tableId, NoticeKind kind, String error, LocalDateTime at) {
        NoticeLog log = NoticeLog.of(sessionId, tableId, kind, NoticeChannel.CHAT, 1, error);
        ReflectionTestUtils.setField(log, "createdAt", at);
        return log;
    }

    private DiningTable table() {
        DiningTable table = DiningTable.builder().eventDate(LocalDate.now()).groupSize(4).algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
        return table;
    }

    @Test
    @DisplayName("실패 기록: 같은 테이블·종류·채널의 기존 시도 수 + 1을 attempt로, 사유는 300자까지")
    void recordFailure_countsAttemptAndTruncatesError() {
        // given
        given(diningTableRepository.findSessionIdById(tableId)).willReturn(Optional.of(sessionId));
        given(noticeLogRepository.countByTableIdAndKindAndChannel(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT))
                .willReturn(2L);

        // when
        noticeLogService.recordFailure(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT,
                new IllegalStateException("x".repeat(400)));

        // then
        ArgumentCaptor<NoticeLog> saved = ArgumentCaptor.forClass(NoticeLog.class);
        then(noticeLogRepository).should().save(saved.capture());
        assertThat(saved.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getValue().getAttempt()).isEqualTo(3);
        assertThat(saved.getValue().getStatus()).isEqualTo(NoticeResult.FAILED);
        assertThat(saved.getValue().getError()).hasSize(300);
    }

    @Test
    @DisplayName("성공 기록: 첫 시도는 attempt 1, 사유 없음")
    void recordSuccess_firstAttempt() {
        // given
        given(diningTableRepository.findSessionIdById(tableId)).willReturn(Optional.of(sessionId));

        // when
        noticeLogService.recordSuccess(tableId, NoticeKind.REMINDER, NoticeChannel.IN_APP);

        // then
        ArgumentCaptor<NoticeLog> saved = ArgumentCaptor.forClass(NoticeLog.class);
        then(noticeLogRepository).should().save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(NoticeKind.REMINDER);
        assertThat(saved.getValue().getChannel()).isEqualTo(NoticeChannel.IN_APP);
        assertThat(saved.getValue().getAttempt()).isEqualTo(1);
        assertThat(saved.getValue().getStatus()).isEqualTo(NoticeResult.SUCCESS);
        assertThat(saved.getValue().getError()).isNull();
    }

    @Test
    @DisplayName("이력 기록이 실패해도 발송 흐름에 예외를 넘기지 않는다")
    void recordSuccess_saveFails_swallowed() {
        // given
        given(diningTableRepository.findSessionIdById(tableId)).willReturn(Optional.of(sessionId));
        willThrow(new IllegalStateException("db down")).given(noticeLogRepository).save(any());

        // when & then
        assertThatCode(() -> noticeLogService.recordSuccess(tableId, NoticeKind.CONTENTS, NoticeChannel.CHAT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회차 발송 이력: 최신순, 테이블 이름은 테이블 탭 순서 번호, 목록에 없는(해체된) 테이블은 null")
    void listSessionNotices_labelsTables() {
        // given
        DiningTable first = table();
        DiningTable second = table();
        UUID dissolvedId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        given(diningTableRepository.findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(eq(sessionId), anyCollection()))
                .willReturn(List.of(first, second));
        given(noticeLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)).willReturn(List.of(
                log(second.getId(), NoticeKind.CONFIRM_MESSAGE, "채팅방 생성 실패", now),
                log(dissolvedId, NoticeKind.NOTIFICATION, null, now.minusMinutes(1)),
                log(first.getId(), NoticeKind.CONFIRM_MESSAGE, null, now.minusMinutes(2))));

        // when
        List<NoticeLogResponse> notices = noticeLogService.listSessionNotices(sessionId);

        // then
        then(gatheringService).should().findRandomTableSession(sessionId);
        assertThat(notices)
                .extracting(NoticeLogResponse::getTableId, NoticeLogResponse::getTableLabel, NoticeLogResponse::getStatus,
                        NoticeLogResponse::getError)
                .containsExactly(
                        tuple(second.getId(), "테이블 2", NoticeResult.FAILED, "채팅방 생성 실패"),
                        tuple(dissolvedId, null, NoticeResult.SUCCESS, null),
                        tuple(first.getId(), "테이블 1", NoticeResult.SUCCESS, null));
    }

    @Test
    @DisplayName("테이블 안내 상태: 최신 CONFIRM_MESSAGE 시도가 성공이면 SENT, 실패면 FAILED, 시도가 없으면 결과에 없다")
    void findConfirmNoticeStatuses_latestAttemptWins() {
        // given
        UUID retried = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        given(noticeLogRepository.findByTableIdInAndKindOrderByCreatedAtDesc(List.of(retried, failed, none),
                NoticeKind.CONFIRM_MESSAGE)).willReturn(List.of(
                log(retried, NoticeKind.CONFIRM_MESSAGE, null, now),
                log(failed, NoticeKind.CONFIRM_MESSAGE, "실패", now.minusMinutes(1)),
                log(retried, NoticeKind.CONFIRM_MESSAGE, "실패", now.minusMinutes(5))));

        // when
        Map<UUID, TableNoticeStatus> statuses = noticeLogService.findConfirmNoticeStatuses(List.of(retried, failed, none));

        // then
        assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(
                retried, TableNoticeStatus.SENT,
                failed, TableNoticeStatus.FAILED));
    }
}
