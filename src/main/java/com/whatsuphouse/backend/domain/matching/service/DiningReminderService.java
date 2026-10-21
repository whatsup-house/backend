package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.matching.entity.DiningContent;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.DiningContentKind;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.repository.DiningContentRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningTableLifecycleEvent;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 우연한 식탁 확정 테이블의 회차 전후 안내. (설계 4.9, KAN-349)
 * - 리마인드: 회차 시작 23~25시간 전, 멤버에게 DINING_REMINDER + 채팅방 안내(시간·지역·식당, 시스템 관리자 이름 메시지). 테이블마다 1회(reminder_sent_at).
 * - 대화 콘텐츠: 회차 시작 ±10분, 채팅방에 대화 주제 3개 + 아이스브레이킹 1개. 테이블마다 1회(contents_posted_at), 채팅방이 없으면 건너뛴다.
 * 테이블마다 새 트랜잭션이라 한 테이블의 실패가 다른 테이블을 막지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiningReminderService {

    static final String REMINDER_FAILURE_PREFIX = "[리마인드] ";
    static final int TOPIC_COUNT = 3;
    private static final Duration REMINDER_FROM = Duration.ofHours(23);
    private static final Duration REMINDER_TO = Duration.ofHours(25);
    private static final Duration CONTENTS_WINDOW = Duration.ofMinutes(10);
    private static final String REMINDER_HEADLINE = "내일 우연한 식탁이 열려요! 일정과 식당을 다시 확인해 주세요.";

    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final DiningContentRepository diningContentRepository;
    private final DiningMatchService diningMatchService;
    private final DiningConfirmService diningConfirmService;
    private final ChatService chatService;
    private final ExceptionCaseService exceptionCaseService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final NoticeLogService noticeLogService;

    // ── 리마인드 ────────────────────────────────────────────────────────────

    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (DiningTable table : findConfirmedStartingBetween(now.plus(REMINDER_FROM), now.plus(REMINDER_TO))) {
            if (table.getReminderSentAt() != null) {
                continue;
            }
            try {
                sendReminder(table.getId(), now);
            } catch (Exception e) {
                log.error("테이블 리마인드 실패: tableId={}", table.getId(), e);
            }
        }
    }

    // 선점 + 알림 이벤트(리스너가 커밋 뒤 적재)가 한 트랜잭션. 채팅방 안내는 별도 트랜잭션이라 실패해도 알림은 남고 예외함(NOTIFICATION)에 기록된다.
    private void sendReminder(UUID tableId, LocalDateTime now) {
        ReminderNotice notice = inNewTransaction(() -> {
            if (diningTableRepository.markReminderSent(tableId, now) == 0) {
                return null; // 다른 실행이 이미 보냈다
            }
            DiningTable table = findTable(tableId);
            UUID sessionId = table.getSession().getId();
            eventPublisher.publishEvent(new DiningTableLifecycleEvent(NotificationType.DINING_REMINDER, tableId, sessionId,
                    findActiveMembers(tableId).stream()
                            .map(member -> member.getApplication().getUser())
                            .filter(Objects::nonNull)
                            .map(User::getId)
                            .distinct()
                            .toList()));
            // 회원별 적재는 커밋 뒤 리스너가 3회까지 시도한다(실패는 예외함). 여기서는 발행을 남긴다.
            noticeLogService.recordSuccess(tableId, NoticeKind.REMINDER, NoticeChannel.IN_APP);
            return table.getChatRoomId() == null ? null
                    : new ReminderNotice(table.getChatRoomId(), sessionId, diningConfirmService.tableNotice(table, REMINDER_HEADLINE));
        });
        if (notice == null) {
            return;
        }
        try {
            // SYSTEM 공지는 채팅 푸시가 가지 않아 시스템 관리자 이름 메시지로 보낸다. (KAN-438)
            inNewTransaction(() -> {
                diningConfirmService.sendAsOperator(notice.roomId(), diningConfirmService.systemAdminId(), notice.text());
                return null;
            });
            noticeLogService.recordSuccess(tableId, NoticeKind.REMINDER, NoticeChannel.CHAT);
        } catch (Exception operatorFailure) {
            // 시스템 관리자가 방 멤버가 아니면(관리자 수동 재시도로 만든 방 등) 운영자 메시지가 실패한다 → 예전처럼 SYSTEM 공지로 대신 보낸다
            log.warn("테이블 리마인드 운영자 메시지 실패, 시스템 공지로 대체: tableId={}", tableId, operatorFailure);
            try {
                inNewTransaction(() -> {
                    chatService.postSystemNotice(notice.roomId(), notice.text());
                    return null;
                });
                // 안내는 전달됐으므로 리마인드 채팅 1건 성공으로 남긴다(앞선 운영자 메시지 실패는 따로 남기지 않는다).
                noticeLogService.recordSuccess(tableId, NoticeKind.REMINDER, NoticeChannel.CHAT);
                return;
            } catch (Exception noticeFailure) {
                operatorFailure.addSuppressed(noticeFailure);
            }
            Exception e = operatorFailure;
            log.warn("테이블 리마인드 채팅 안내 실패: tableId={}", tableId, e);
            noticeLogService.recordFailure(tableId, NoticeKind.REMINDER, NoticeChannel.CHAT, e);
            inNewTransaction(() -> exceptionCaseService.open(ExceptionCaseType.NOTIFICATION, notice.sessionId(), tableId, null,
                    REMINDER_FAILURE_PREFIX + "채팅방 리마인드 안내 실패: " + e.getMessage()));
        }
    }

    private record ReminderNotice(UUID roomId, UUID sessionId, String text) {
    }

    // ── 대화 콘텐츠 ─────────────────────────────────────────────────────────

    public void postDueContents() {
        LocalDateTime now = LocalDateTime.now();
        for (DiningTable table : findConfirmedStartingBetween(now.minus(CONTENTS_WINDOW), now.plus(CONTENTS_WINDOW))) {
            if (table.getContentsPostedAt() != null || table.getChatRoomId() == null) {
                continue;
            }
            try {
                inNewTransaction(() -> {
                    postContents(table.getId(), now);
                    return null;
                });
            } catch (Exception e) {
                log.error("테이블 대화 콘텐츠 게시 실패: tableId={}", table.getId(), e);
                noticeLogService.recordFailure(table.getId(), NoticeKind.CONTENTS, NoticeChannel.CHAT, e);
            }
        }
    }

    // 선점과 게시가 한 트랜잭션: 게시가 실패하면 선점도 되돌아가 창 안의 다음 주기에 다시 시도한다.
    private void postContents(UUID tableId, LocalDateTime now) {
        if (diningTableRepository.markContentsPosted(tableId, now) == 0) {
            return;
        }
        DiningTable table = findTable(tableId);
        List<Application> applications = findActiveMembers(tableId).stream().map(DiningTableMember::getApplication).toList();
        List<MatchingEngine.Applicant> profiles = List.copyOf(diningMatchService.findProfiles(applications).values());
        // 멤버마다 MY_STYLE·WANTED_STYLE 답을 합쳐 한 번만 센다.
        List<Set<String>> styles = profiles.stream()
                .map(profile -> {
                    Set<String> style = new HashSet<>(profile.myStyle());
                    style.addAll(profile.wantedStyle());
                    return style;
                })
                .toList();
        // 테이블마다 다른 조합이 나오도록 테이블 ID로 섞는다(같은 테이블은 재시도해도 같은 조합).
        List<DiningContent> picked = pickContents(diningContentRepository.findAll(),
                profiles.stream().map(MatchingEngine.Applicant::interests).toList(), styles,
                new Random(tableId.getMostSignificantBits()));
        // 참가자 테이블 상세가 채팅방과 같은 콘텐츠를 보여 주도록 올린 콘텐츠를 남긴다. (KAN-438)
        table.recordPostedContents(picked.stream().map(DiningContent::getId).toList());
        if (!picked.isEmpty()) {
            chatService.postSystemNotice(table.getChatRoomId(), contentsNotice(picked));
            noticeLogService.recordSuccess(tableId, NoticeKind.CONTENTS, NoticeChannel.CHAT);
        }
    }

    /**
     * 대화 주제 3개 + 아이스브레이킹 1개. 주제는 태그마다 1개씩, 이 순서로 채운다. (KAN-438)
     * 1) 멤버 두 명 이상이 고른 관심사(많이 겹친 순) 2) 멤버 성향(MY_STYLE·WANTED_STYLE 답, 많이 고른 순) 3) 범용(태그 없음).
     * 같은 조건 안에서는 random 순서로 고른다.
     */
    static List<DiningContent> pickContents(List<DiningContent> contents, List<Set<String>> memberInterests,
                                            List<Set<String>> memberStyles, Random random) {
        List<DiningContent> picked = new ArrayList<>();
        List<String> tags = new ArrayList<>(rankedTags(memberInterests, 2));
        tags.addAll(rankedTags(memberStyles, 1));
        for (String tag : tags) {
            if (picked.size() == TOPIC_COUNT) {
                break;
            }
            shuffled(contents, c -> c.getKind() == DiningContentKind.TOPIC && tag.equals(c.getInterestTag()), random)
                    .stream().findFirst().ifPresent(picked::add);
        }
        shuffled(contents, c -> c.getKind() == DiningContentKind.TOPIC && c.getInterestTag() == null, random).stream()
                .limit(TOPIC_COUNT - picked.size())
                .forEach(picked::add);
        shuffled(contents, c -> c.getKind() == DiningContentKind.ICEBREAKER, random).stream()
                .findFirst().ifPresent(picked::add);
        return picked;
    }

    // minCount명 이상이 고른 값, 많이 고른 순(같으면 가나다순)
    private static List<String> rankedTags(List<Set<String>> memberTags, int minCount) {
        Map<String, Long> tagCounts = memberTags.stream()
                .flatMap(Set::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return tagCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= minCount)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<DiningContent> shuffled(List<DiningContent> contents, Predicate<DiningContent> filter, Random random) {
        List<DiningContent> result = new ArrayList<>(contents.stream().filter(filter).toList());
        Collections.shuffle(result, random);
        return result;
    }

    private static String contentsNotice(List<DiningContent> picked) {
        List<String> lines = new ArrayList<>();
        lines.add("오늘의 대화 주제");
        List<DiningContent> topics = picked.stream().filter(c -> c.getKind() == DiningContentKind.TOPIC).toList();
        for (int i = 0; i < topics.size(); i++) {
            lines.add((i + 1) + ". " + topics.get(i).getBody());
        }
        picked.stream().filter(c -> c.getKind() == DiningContentKind.ICEBREAKER).findFirst()
                .ifPresent(c -> lines.add("아이스브레이킹: " + c.getBody()));
        return String.join("\n", lines);
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    // 시작 시각이 from~to(포함)인 확정 테이블. 취소된 회차는 뺀다.
    private List<DiningTable> findConfirmedStartingBetween(LocalDateTime from, LocalDateTime to) {
        return diningTableRepository.findWithSessionByStatusAndEventDateBetween(
                        DiningTableStatus.CONFIRMED, from.toLocalDate(), to.toLocalDate()).stream()
                .filter(table -> table.getSession().getStatus() != GatheringSessionStatus.CANCELLED)
                .filter(table -> {
                    LocalDateTime startAt = table.getSession().getStartAt();
                    return !startAt.isBefore(from) && !startAt.isAfter(to);
                })
                .toList();
    }

    private DiningTable findTable(UUID tableId) {
        return diningTableRepository.findById(tableId)
                .filter(table -> table.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
    }

    // 취소(soft delete)된 신청의 멤버 행은 뺀다.
    private List<DiningTableMember> findActiveMembers(UUID tableId) {
        return diningTableMemberRepository.findByTableIdWithApplication(tableId).stream()
                .filter(member -> member.getApplication().getDeletedAt() == null)
                .toList();
    }

    private <T> T inNewTransaction(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }
}
