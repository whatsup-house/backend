package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.auth.service.DemoAccountService;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DemoResetResponse;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.FeedbackRepository;
import com.whatsuphouse.backend.domain.dining.repository.PeerPreferenceRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 심사용 회차 데이터 초기화(KAN-442). 숨김 우연한 식탁 모임의 회차만 허용한다(아니면 409, 아무것도 지우지 않는다). 한 트랜잭션에서
 * 1. 이 회차 데이터를 FK 순서대로 지운다: 피드백·선호·신고 → 예외 → 테이블 단체방 → 참석·멤버·테이블·매칭 실행·안내 이력·해결 선택
 *    → 식당 사용 수 0 → 송금 요청·보증금 → 이용권(전용 1회권 삭제, 다른 이용권 차감 복구) → 신청(배정됐거나 배정 전 희망, 취소 포함)과 답변·희망·이력.
 *    신청한 회원 계정은 남긴다(다른 회차 신청·계정은 그대로).
 * 2. 매칭 실행 예정 시각을 비워 스케줄러가 자동 매칭하지 않게 하고 모집 중으로 둔다. 매칭은 관리자가 직접 실행한다.
 * 3. 심사용 신청자 16명(SEED_PROFILES)을 결제 완료·매칭 대기로 넣는다. 지역마다 8명 = 4인 테이블 2개(지역당 최소 테이블 수 충족).
 *    시드 계정은 이메일 demo+seed-{회차 ID 앞 8자}-{번호}@whatsup.house 로 다시 쓰므로 여러 번 눌러도 같은 결과다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminDiningDemoService {

    private final GatheringSessionService gatheringSessionService;
    private final MatchingService matchingService;
    private final AdminChatService adminChatService;
    private final AdminApplicationService adminApplicationService;
    private final ApplicationService applicationService;
    private final TicketService ticketService;
    private final DepositService depositService;
    private final DemoAccountService demoAccountService;
    private final FeedbackRepository feedbackRepository;
    private final PeerPreferenceRepository peerPreferenceRepository;
    private final SafetyReportRepository safetyReportRepository;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final SessionVenueRepository sessionVenueRepository;

    /** 시드 신청자 1명. 답은 표준 질문(reserved_key)에 그대로 들어간다. */
    record SeedProfile(Gender gender, int birthYear, Mbti mbti, Job job, DiningRegion region,
                       List<String> interests, List<String> myStyle, List<String> wantedStyle, String diet) {
    }

    private static final String MOVIE = "영화·드라마";
    private static final String MUSIC = "음악";
    private static final String BOOK = "독서·글쓰기";
    private static final String SPORTS = "운동 (헬스, 러닝, 클라이밍 등)";
    private static final String FOOD = "맛집·카페 탐방";
    private static final String TRAVEL = "여행";
    private static final String PHOTO = "사진·영상";
    private static final String ART = "전시·공연·아트";
    private static final String STUDY = "자기계발·공부";
    private static final String CALM = "잔잔하고 조용한 편";
    private static final String ENERGETIC = "활기차고 에너지 있는 편";
    private static final String DEEP_TALK = "깊은 대화를 좋아함";
    private static final String HUMOR = "가볍고 유머 있는 대화를 좋아함";
    private static final String LISTENER = "경청을 잘 함";
    private static final String LEADER = "이야기를 이끄는 편";

    /**
     * 4명 묶음 4개(지역마다 2개). 묶음마다 남2·여2, 남녀 쌍 4개가 MBTI 궁합표 1.0, 관심사 3개 공유(+1개), 원하는 스타일이 서로의 내 스타일에 들어 있다.
     * 출생연도는 같은 성별끼리 같고 남녀 1년 차. 묶음끼리는 관심사·스타일이 겹치지 않아 엔진이 묶음대로 나눈다.
     * 기본 규칙(나이 차 8, 4~6인, 기본 가중치)에서 그룹 점수 A 0.82 · B 0.81 · C 0.82 · D 0.82 (AdminDiningDemoServiceTest가 엔진으로 확인).
     */
    static final List<SeedProfile> SEED_PROFILES = List.of(
            // A 홍대/합정: 영화·책·전시, 깊은 대화
            new SeedProfile(Gender.MALE, 1996, Mbti.ENFP, Job.UX_UI_DESIGNER, DiningRegion.HONGDAE,
                    List.of(MOVIE, BOOK, ART, MUSIC), List.of(DEEP_TALK, LISTENER), List.of(DEEP_TALK), "없음"),
            new SeedProfile(Gender.MALE, 1996, Mbti.ENTP, Job.EDITOR, DiningRegion.HONGDAE,
                    List.of(MOVIE, BOOK, ART), List.of(DEEP_TALK, LISTENER), List.of(DEEP_TALK), "없음"),
            new SeedProfile(Gender.FEMALE, 1997, Mbti.INFJ, Job.PRODUCT_MANAGER, DiningRegion.HONGDAE,
                    List.of(MOVIE, BOOK, ART, MUSIC), List.of(DEEP_TALK, LISTENER), List.of(DEEP_TALK), "갑각류 알레르기"),
            new SeedProfile(Gender.FEMALE, 1997, Mbti.INTJ, Job.ILLUSTRATOR, DiningRegion.HONGDAE,
                    List.of(MOVIE, BOOK, ART), List.of(DEEP_TALK, LISTENER), List.of(DEEP_TALK), "없음"),
            // B 홍대/합정: 음악·맛집·여행, 활기·유머
            new SeedProfile(Gender.MALE, 1993, Mbti.ENFJ, Job.CONTENT_CREATOR, DiningRegion.HONGDAE,
                    List.of(MUSIC, FOOD, TRAVEL, PHOTO), List.of(ENERGETIC, HUMOR), List.of(HUMOR), "없음"),
            new SeedProfile(Gender.MALE, 1993, Mbti.ENTJ, Job.BRAND_MARKETER, DiningRegion.HONGDAE,
                    List.of(MUSIC, FOOD, TRAVEL), List.of(ENERGETIC, HUMOR), List.of(HUMOR), "없음"),
            new SeedProfile(Gender.FEMALE, 1994, Mbti.INFP, Job.BARISTA, DiningRegion.HONGDAE,
                    List.of(MUSIC, FOOD, TRAVEL), List.of(ENERGETIC, HUMOR), List.of(HUMOR), "오이 빼 주세요"),
            new SeedProfile(Gender.FEMALE, 1994, Mbti.INTP, Job.PHOTOGRAPHER, DiningRegion.HONGDAE,
                    List.of(MUSIC, FOOD, TRAVEL, PHOTO), List.of(ENERGETIC, HUMOR), List.of(HUMOR), "없음"),
            // C 강남/신논현: 운동·자기계발·맛집, 활기·이끄는 편
            new SeedProfile(Gender.MALE, 1995, Mbti.ESFP, Job.SEMICONDUCTOR_ENGINEER, DiningRegion.GANGNAM,
                    List.of(SPORTS, STUDY, FOOD, TRAVEL), List.of(ENERGETIC, LEADER), List.of(ENERGETIC), "없음"),
            new SeedProfile(Gender.MALE, 1995, Mbti.ESTP, Job.BANKER, DiningRegion.GANGNAM,
                    List.of(SPORTS, STUDY, FOOD), List.of(ENERGETIC, LEADER), List.of(ENERGETIC), "없음"),
            new SeedProfile(Gender.FEMALE, 1996, Mbti.ISFJ, Job.NURSE, DiningRegion.GANGNAM,
                    List.of(SPORTS, STUDY, FOOD, TRAVEL), List.of(ENERGETIC, LEADER), List.of(ENERGETIC), "없음"),
            new SeedProfile(Gender.FEMALE, 1996, Mbti.ISTJ, Job.TEACHER, DiningRegion.GANGNAM,
                    List.of(SPORTS, STUDY, FOOD), List.of(ENERGETIC, LEADER), List.of(ENERGETIC), "견과류 알레르기"),
            // D 강남/신논현: 영화·책·음악, 잔잔·경청
            new SeedProfile(Gender.MALE, 1992, Mbti.ESFJ, Job.ARCHITECT, DiningRegion.GANGNAM,
                    List.of(MOVIE, BOOK, MUSIC, ART), List.of(CALM, LISTENER), List.of(LISTENER), "없음"),
            new SeedProfile(Gender.MALE, 1992, Mbti.ESTJ, Job.ACCOUNTANT, DiningRegion.GANGNAM,
                    List.of(MOVIE, BOOK, MUSIC), List.of(CALM, LISTENER), List.of(LISTENER), "없음"),
            new SeedProfile(Gender.FEMALE, 1993, Mbti.ISFP, Job.PHARMACIST, DiningRegion.GANGNAM,
                    List.of(MOVIE, BOOK, MUSIC, ART), List.of(CALM, LISTENER), List.of(LISTENER), "채식(페스코)"),
            new SeedProfile(Gender.FEMALE, 1993, Mbti.ISTP, Job.HR, DiningRegion.GANGNAM,
                    List.of(MOVIE, BOOK, MUSIC), List.of(CALM, LISTENER), List.of(LISTENER), "없음"));

    public DemoResetResponse resetSession(UUID sessionId) {
        GatheringSession session = gatheringSessionService.findSessionForUpdate(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        Gathering gathering = session.getGathering();
        if (gathering.getDeletedAt() != null || gathering.getGatheringType() != GatheringType.RANDOM_TABLE
                || !gathering.isHidden()) {
            throw new CustomException(ErrorCode.DEMO_RESET_NOT_ALLOWED);
        }

        // 1. 정리 (FK: 자식 → 부모)
        MatchingService.SessionTables tables = matchingService.findSessionTables(sessionId);
        List<UUID> applicationIds = adminApplicationService.findSessionApplicationIdsIncludingCancelled(sessionId);
        if (!tables.memberIds().isEmpty()) {
            feedbackRepository.deleteByTableMemberIdIn(tables.memberIds());
        }
        if (!tables.tableIds().isEmpty()) {
            peerPreferenceRepository.deleteByTableIdIn(tables.tableIds());
            safetyReportRepository.deleteByTableIdIn(tables.tableIds());
        }
        exceptionCaseRepository.deleteBySessionOrTablesOrApplications(sessionId, tables.tableIds(), applicationIds);
        adminChatService.purgeDiningTableRooms(tables.tableIds());
        matchingService.deleteSessionMatching(sessionId, tables, applicationIds);
        sessionVenueRepository.resetUsedTables(sessionId);
        List<UUID> dedicatedPassIds = ticketService.findDedicatedPassIds(applicationIds);
        depositService.deleteForApplications(applicationIds, dedicatedPassIds);
        ticketService.deleteApplicationTickets(applicationIds, dedicatedPassIds);
        adminApplicationService.deleteApplications(applicationIds);

        // 2. 자동 매칭 끄기
        session.resetForDemo();

        // 3. 시드
        String prefix = "demo+seed-" + sessionId.toString().substring(0, 8) + "-";
        for (int i = 0; i < SEED_PROFILES.size(); i++) {
            SeedProfile profile = SEED_PROFILES.get(i);
            User user = demoAccountService.findOrCreateSeedUser(String.format("%s%02d@whatsup.house", prefix, i + 1),
                    profile.gender(), profile.birthYear(), profile.mbti(), profile.job());
            Application application = applicationService.createDemoSeedApplication(session, user,
                    List.of(profile.region()), answers(profile));
            if (application.getStatus() == ApplicationStatus.PAYMENT_PENDING) {
                ticketService.settleDemoSeedTicket(user, application);
            }
        }
        return DemoResetResponse.builder()
                .deletedApplicationCount(applicationIds.size())
                .deletedTableCount(tables.tableIds().size())
                .seededApplicantCount(SEED_PROFILES.size())
                .build();
    }

    static Map<ReservedQuestionKey, Object> answers(SeedProfile profile) {
        Map<ReservedQuestionKey, Object> answers = new EnumMap<>(ReservedQuestionKey.class);
        answers.put(ReservedQuestionKey.BIRTH_YEAR, profile.birthYear());
        answers.put(ReservedQuestionKey.GENDER, profile.gender().name());
        answers.put(ReservedQuestionKey.MBTI, profile.mbti().name());
        answers.put(ReservedQuestionKey.INTERESTS, profile.interests());
        answers.put(ReservedQuestionKey.MY_STYLE, profile.myStyle());
        answers.put(ReservedQuestionKey.WANTED_STYLE, profile.wantedStyle());
        answers.put(ReservedQuestionKey.DIET, profile.diet());
        // 모두 같은 가격대라 테이블 식당 배정 때 이 가격대 식당을 먼저 고른다
        answers.put(ReservedQuestionKey.PRICE_RANGE, List.of(PriceRangeOption.MID.getLabel()));
        return answers;
    }
}
