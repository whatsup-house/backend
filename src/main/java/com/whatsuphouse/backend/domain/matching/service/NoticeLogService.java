package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.dto.response.NoticeLogResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.NoticeLog;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.enums.NoticeResult;
import com.whatsuphouse.backend.domain.matching.enums.TableNoticeStatus;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.NoticeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 우연한 식탁 안내 발송 이력(INF-08, KAN-437). 확정 채팅 메시지·리마인드·콘텐츠 게시·확정 인앱 알림을 보낸 지점이 시도마다 1건씩 남긴다.
 * 호출 측 트랜잭션이 있으면 함께 커밋되고(롤백되면 함께 사라진다), 없으면(실패 단계는 이미 롤백됨) 새 트랜잭션에서 남긴다.
 * 이력 기록이 실패해도 발송 흐름(다음 단계·다음 테이블)을 막지 않도록 예외를 삼키고 로그만 남긴다.
 * 기록 메서드에는 @Transactional을 두지 않는다(읽기 전용 트랜잭션에 묶이면 INSERT가 반영되지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeLogService {

    private static final Set<DiningTableStatus> VISIBLE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);

    private final NoticeLogRepository noticeLogRepository;
    private final DiningTableRepository diningTableRepository;
    private final GatheringService gatheringService;
    private final PlatformTransactionManager transactionManager;

    public void recordSuccess(UUID tableId, NoticeKind kind, NoticeChannel channel) {
        record(tableId, kind, channel, null, false);
    }

    public void recordFailure(UUID tableId, NoticeKind kind, NoticeChannel channel, Exception error) {
        record(tableId, kind, channel, errorMessage(error), false);
    }

    /**
     * 커밋 뒤 리스너(@TransactionalEventListener AFTER_COMMIT)에서 남길 때. error가 null이면 성공.
     * 끝난 트랜잭션에 참여하면 INSERT가 커밋되지 않아 항상 새 트랜잭션으로 남긴다. (KAN-438 웹 푸시)
     */
    public void recordAfterCommit(UUID tableId, NoticeKind kind, NoticeChannel channel, Exception error) {
        record(tableId, kind, channel, error == null ? null : errorMessage(error), true);
    }

    private static String errorMessage(Exception error) {
        String message = error.getMessage();
        return message != null && !message.isBlank() ? message : error.getClass().getSimpleName();
    }

    // 테이블이 없으면(삭제) 남기지 않는다.
    private void record(UUID tableId, NoticeKind kind, NoticeChannel channel, String error, boolean newTransaction) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        if (newTransaction) {
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        try {
            template.executeWithoutResult(status ->
                    diningTableRepository.findSessionIdById(tableId).ifPresent(sessionId -> {
                        int attempt = (int) noticeLogRepository.countByTableIdAndKindAndChannel(tableId, kind, channel) + 1;
                        noticeLogRepository.save(NoticeLog.of(sessionId, tableId, kind, channel, attempt, error));
                    }));
        } catch (RuntimeException e) {
            log.warn("안내 발송 이력 기록 실패: tableId={}, kind={}, channel={}", tableId, kind, channel, e);
        }
    }

    /** 회차 안내 발송 이력, 최신순. 테이블 이름은 테이블 탭과 같은 순서(생성 순)의 번호. 우연한 식탁 회차가 아니면 400, 없으면 404. */
    @Transactional(readOnly = true)
    public List<NoticeLogResponse> listSessionNotices(UUID sessionId) {
        gatheringService.findRandomTableSession(sessionId);
        List<DiningTable> tables = diningTableRepository
                .findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(sessionId, VISIBLE_TABLE_STATUSES);
        Map<UUID, String> labels = new HashMap<>();
        for (int i = 0; i < tables.size(); i++) {
            labels.put(tables.get(i).getId(), "테이블 " + (i + 1));
        }
        return noticeLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
                .map(log -> NoticeLogResponse.of(log, log.getTableId() != null ? labels.get(log.getTableId()) : null))
                .toList();
    }

    /** 테이블 ID → 최신 확정 안내(CONFIRM_MESSAGE) 결과. 시도가 없는 테이블은 결과에 없다(NONE). */
    @Transactional(readOnly = true)
    public Map<UUID, TableNoticeStatus> findConfirmNoticeStatuses(Collection<UUID> tableIds) {
        Map<UUID, TableNoticeStatus> result = new HashMap<>();
        if (tableIds.isEmpty()) {
            return result;
        }
        // 최신순이라 테이블별 첫 행이 최신 시도다.
        noticeLogRepository.findByTableIdInAndKindOrderByCreatedAtDesc(tableIds, NoticeKind.CONFIRM_MESSAGE)
                .forEach(log -> result.putIfAbsent(log.getTableId(),
                        log.getStatus() == NoticeResult.SUCCESS ? TableNoticeStatus.SENT : TableNoticeStatus.FAILED));
        return result;
    }
}
