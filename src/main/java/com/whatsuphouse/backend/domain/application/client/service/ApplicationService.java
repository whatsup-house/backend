package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.AnswerItem;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationCreateRequest;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.auth.event.GuestEmailVerificationConsumedEvent;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.form.enums.SystemQuestionKey;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationApprovedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPendingEvent;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    // 타 도메인은 Repository 대신 Service로 접근한다. (KAN-393)
    private final GatheringSessionService gatheringSessionService;
    private final UserService userService;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FormProvisionService formProvisionService;
    private final TicketService ticketService;
    private final AuthService authService;
    private final ApplicationLookupTokenService applicationLookupTokenService;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_BIRTH_YEAR = 1900;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * 모임 신청: 종류 + 희망 회차(요청 순서 = 우선순위). (KAN-338)
     * 일반 모임은 회차 정확히 1개이고 그 회차로 바로 배정된다. 우연한 식탁은 1개 이상이고 매칭 전까지 배정 회차가 없다.
     */
    @Transactional
    public ApplicationResponse apply(ApplicationCreateRequest request, UUID userId) {
        List<UUID> candidateIds = request.getCandidateSessionIds().stream().distinct().toList();
        // 정원 체크~신청 저장 구간의 동시 신청 race를 막기 위해 회차 행을 잠근다. 교착을 피하려 ID 순으로 잠근다.
        Map<UUID, GatheringSession> locked = new HashMap<>();
        candidateIds.stream().sorted().forEach(id -> {
            GatheringSession session = gatheringSessionService.findSessionForUpdate(id)
                    .filter(s -> s.getGathering().getDeletedAt() == null)
                    .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
            // 다른 종류의 회차가 섞이면 잘못된 요청이다. (KAN-342)
            if (!session.getGathering().getId().equals(request.getGatheringId())) {
                throw new CustomException(ErrorCode.SESSION_GATHERING_MISMATCH);
            }
            locked.put(id, session);
        });
        return applyInternal(candidateIds.stream().map(locked::get).toList(), request.getAnswers(), userId);
    }

    // 기존 신청 경로: 경로의 gatheringId는 회차 ID다(마이그레이션된 회차는 옛 게더링 ID와 같다). 그 회차 하나를 고른 신청으로 처리한다.
    @Transactional
    public ApplicationResponse apply(UUID sessionId, ApplicationRequest request, UUID userId) {
        return applyInternal(List.of(lockSession(sessionId)), request.getAnswers(), userId);
    }

    @Transactional
    public ApplicationResponse applyAsGuest(UUID sessionId, ApplicationRequest request) {
        return applyInternal(List.of(lockSession(sessionId)), request.getAnswers(), null);
    }

    private GatheringSession lockSession(UUID sessionId) {
        return gatheringSessionService.findSessionForUpdate(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
    }

    // applyInternal은 @Transactional 퍼블릭 메서드에서만 호출되므로 self-invocation 트랜잭션 누락 위험 없음
    // candidates: 같은 종류의 잠근 회차들, 희망 순위 순.
    @SuppressWarnings("java:S6809")
    private ApplicationResponse applyInternal(List<GatheringSession> candidates, List<AnswerItem> answers, UUID userId) {
        Gathering gathering = candidates.get(0).getGathering();
        boolean randomTable = gathering.getGatheringType() == GatheringType.RANDOM_TABLE;
        if (!randomTable && candidates.size() != 1) {
            throw new CustomException(ErrorCode.SINGLE_SESSION_REQUIRED);
        }

        // eventDate가 지난 모집중 회차는 모집중이 아니다(KAN-163). 신청 마감이 지난 회차도 막는다.
        LocalDateTime now = LocalDateTime.now();
        candidates.forEach(candidate -> candidate.validateApplicable(now));

        // 우연한 식탁은 회원 전용이다. 비회원은 신청 단계에서 차단한다.
        if (randomTable && userId == null) {
            throw new CustomException(ErrorCode.RANDOM_TABLE_MEMBERS_ONLY);
        }

        // 배정 회차: 일반 모임은 신청 회차, 우연한 식탁은 매칭 전이라 비워 둔다.
        GatheringSession session = randomTable ? null : candidates.get(0);

        // 정원은 관리자가 승인(CONFIRMED)·출석(ATTENDED) 처리한 인원만 차지한다. PENDING 신청은 정원과 무관. (KAN-236)
        // 우연한 식탁은 매칭이 회차별 인원을 정하므로 신청 단계에서 세지 않는다.
        if (session != null) {
            int occupiedSeats = applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                    session.getId(), ApplicationStatus.SEAT_OCCUPYING);
            if (occupiedSeats >= session.getMaxAttendees()) {
                throw new CustomException(ErrorCode.GATHERING_FULL);
            }
        }

        // 폼은 종류 단위다. 폼이 없는 게더링(시드/레거시)도 신청 가능하도록 기본 폼을 프로비저닝한다. (KAN-206)
        Form form = formProvisionService.findOrCreateForm(gathering);

        List<FormQuestion> questions = formProvisionService.findQuestions(form);

        Map<UUID, FormQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(FormQuestion::getId, q -> q));

        // 회원은 이름/연락처를 계정에서 가져오므로 시스템 예약 질문은 필수 검증에서 제외한다.
        validateAnswers(answers, questions, questionMap, userId != null);

        // questionKey → value 맵
        Map<String, Object> byKey = buildAnswersByKey(answers, questionMap);

        User user = null;
        if (userId != null) {
            user = userService.findUser(userId);
            // 우연한 식탁은 고른 희망 회차 중 하나라도 이미 신청했으면 중복이다.
            boolean alreadyApplied = session != null
                    ? applicationRepository.existsBySession_IdAndUser_IdAndDeletedAtIsNull(session.getId(), userId)
                    : applicationCandidateSessionRepository
                            .existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
                                    candidates.stream().map(GatheringSession::getId).toList(), userId);
            if (alreadyApplied) {
                throw new CustomException(ErrorCode.ALREADY_APPLIED);
            }
        } else {
            // 비회원은 일반 모임만 신청할 수 있으므로(위에서 검사) 배정 회차가 항상 있다.
            String guestPhone = extractString(byKey, SystemQuestionKey.PHONE.getKey());
            if (guestPhone == null || guestPhone.isBlank()) {
                throw new CustomException(ErrorCode.GUEST_PHONE_REQUIRED);
            }
            if (applicationRepository.existsBySession_IdAndPhoneAndDeletedAtIsNull(session.getId(), guestPhone)) {
                throw new CustomException(ErrorCode.ALREADY_APPLIED);
            }
            // 이메일 누락은 시스템 예약 질문(required)에서 REQUIRED_ANSWER_MISSING으로 처리된다.
            // 여기서는 값이 있을 때 형식만 검증한다. (폼에 email 질문이 없는 구버전은 통과)
            String guestEmail = extractString(byKey, SystemQuestionKey.EMAIL.getKey());
            if (guestEmail != null && !guestEmail.isBlank() && !EMAIL_PATTERN.matcher(guestEmail).matches()) {
                throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
            }
            if (guestEmail == null || guestEmail.isBlank() || !authService.isGuestEmailVerified(guestEmail)) {
                throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
            }
        }

        String name = user != null ? user.getName() : extractString(byKey, SystemQuestionKey.NAME.getKey());
        String phone = user != null ? user.getPhone() : extractString(byKey, SystemQuestionKey.PHONE.getKey());
        // 알림 발송용 이메일: 회원=계정 이메일, 비회원=신청서 답변 이메일
        String email = user != null ? user.getEmail() : extractString(byKey, SystemQuestionKey.EMAIL.getKey());

        boolean autoConfirmed = false;
        boolean paymentPending = false;
        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
            validateRandomTableEligibility(user);
        }

        Application application = Application.builder()
                .bookingNumber(generateBookingNumber())
                .gathering(gathering)
                .session(session)
                .user(user)
                .name(name)
                .phone(phone)
                .email(email)
                .formSnapshot(buildFormSnapshot(questions))
                .build();

        Application saved = applicationRepository.save(application);
        // 희망 회차: 요청 순서대로 1순위부터. 일반 모임은 신청 회차 1행.
        for (int i = 0; i < candidates.size(); i++) {
            applicationCandidateSessionRepository.save(new ApplicationCandidateSession(saved, candidates.get(i), i + 1));
        }
        if (user == null) {
            // 트랜잭션 커밋 후에만 인증을 소비한다. 롤백 시 인증이 남아 재신청 가능. (AFTER_COMMIT 리스너)
            eventPublisher.publishEvent(new GuestEmailVerificationConsumedEvent(email));
        }

        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE
                && user.isApprovedForRandomTable()) {
            if (!saved.requiresRandomTableTicket()) {
                saved.confirm();
                autoConfirmed = true;
            } else {
                autoConfirmed = ticketService.tryUseOneTicket(user, saved);
                paymentPending = !autoConfirmed;
                if (autoConfirmed) {
                    saved.confirm();
                } else {
                    saved.awaitPayment();
                }
            }
        }

        saveAnswers(saved, answers, questionMap);

        if (autoConfirmed) {
            eventPublisher.publishEvent(new ApplicationConfirmedEvent(saved));
        } else if (paymentPending) {
            eventPublisher.publishEvent(new ApplicationApprovedEvent(saved));
        } else {
            eventPublisher.publishEvent(new ApplicationPendingEvent(saved));
        }
        return ApplicationResponse.from(saved);
    }

    private void validateRandomTableEligibility(User user) {
        if (user.isAccountSuspended()) {
            throw new CustomException(ErrorCode.PARTICIPANT_BLOCKED);
        }
        if (user.isRandomTableEligibilityRestricted()) {
            throw new CustomException(ErrorCode.RANDOM_TABLE_ELIGIBILITY_RESTRICTED);
        }
    }

    private void validateAnswers(List<AnswerItem> answers, List<FormQuestion> questions,
                                 Map<UUID, FormQuestion> questionMap, boolean skipReservedRequired) {
        Set<UUID> submittedIds = answers.stream()
                .map(AnswerItem::getQuestionId)
                .collect(Collectors.toSet());

        for (UUID id : submittedIds) {
            if (!questionMap.containsKey(id)) {
                throw new CustomException(ErrorCode.INVALID_QUESTION);
            }
        }

        // 출생연도는 1900~올해(한국 시간) 정수 연도만 받는다. 라벨이 '나이'라 옛 클라이언트가 나이(29)를 보내는 걸 막는다. (KAN-388)
        for (AnswerItem answer : answers) {
            if (questionMap.get(answer.getQuestionId()).getReservedKey() == ReservedQuestionKey.BIRTH_YEAR
                    && !isValidBirthYear(answer.getValue())) {
                throw new CustomException(ErrorCode.INVALID_BIRTH_YEAR);
            }
        }

        for (FormQuestion q : questions) {
            if (skipReservedRequired && q.isSystemReserved()) {
                continue;
            }
            if (q.isRequired() && !submittedIds.contains(q.getId())) {
                throw new CustomException(ErrorCode.REQUIRED_ANSWER_MISSING);
            }
        }
    }

    // 매칭 엔진의 출생연도 읽기(DiningMatchService.year: 숫자 또는 숫자 문자열)와 같은 규칙. 소수는 정수 연도가 아니라 거부한다.
    private static boolean isValidBirthYear(Object value) {
        Integer year = null;
        if (value instanceof Number number) {
            year = number.doubleValue() == number.intValue() ? number.intValue() : null;
        } else if (value instanceof String text) {
            try {
                year = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // 숫자 문자열이 아니면 거부
            }
        }
        return year != null && year >= MIN_BIRTH_YEAR && year <= Year.now(KOREA).getValue();
    }

    private Map<String, Object> buildAnswersByKey(List<AnswerItem> answers, Map<UUID, FormQuestion> questionMap) {
        Map<String, Object> result = new HashMap<>();
        for (AnswerItem item : answers) {
            FormQuestion q = questionMap.get(item.getQuestionId());
            if (q != null) {
                result.put(q.getQuestionKey(), item.getValue());
            }
        }
        return result;
    }

    private void saveAnswers(Application application, List<AnswerItem> answers,
                             Map<UUID, FormQuestion> questionMap) {
        List<ApplicationAnswer> toSave = new ArrayList<>();
        for (AnswerItem item : answers) {
            FormQuestion q = questionMap.get(item.getQuestionId());
            if (q != null) {
                toSave.add(ApplicationAnswer.builder()
                        .application(application)
                        .question(q)
                        .value(Map.of("value", item.getValue()))
                        .build());
            }
        }
        applicationAnswerRepository.saveAll(toSave);
    }

    private Map<String, Object> buildFormSnapshot(List<FormQuestion> questions) {
        List<Map<String, Object>> list = questions.stream().map(q -> {
            Map<String, Object> m = new HashMap<>();
            m.put("questionId", q.getId().toString());
            m.put("questionKey", q.getQuestionKey());
            m.put("type", q.getType().name());
            m.put("label", q.getLabel());
            m.put("required", q.isRequired());
            m.put("displayOrder", q.getDisplayOrder());
            m.put("options", q.getOptions());
            m.put("validation", q.getValidation());
            m.put("isMatchingField", q.isMatchingField());
            return m;
        }).toList();
        return Map.of("questions", list);
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    public ApplicationCheckResponse checkApplication(String phone, String bookingNumber) {
        Application application = applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull(phone, bookingNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        return ApplicationCheckResponse.from(application, loadAnswers(application.getId()), findTicketRemainingCount(application));
    }

    public ApplicationCheckResponse checkApplicationByToken(String token) {
        String bookingNumber = applicationLookupTokenService.extractBookingNumber(token);
        Application application = applicationRepository.findByBookingNumberAndDeletedAtIsNull(bookingNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        return ApplicationCheckResponse.from(application, loadAnswers(application.getId()), findTicketRemainingCount(application));
    }

    public ApplicationCheckResponse getMyApplication(UUID applicationId, UUID userId) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }
        return ApplicationCheckResponse.from(application, loadAnswers(applicationId), findTicketRemainingCount(application));
    }

    private Integer findTicketRemainingCount(Application application) {
        if (application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            return null;
        }
        return ticketService.findBalanceAfterUse(application.getId()).orElse(null);
    }

    private List<AnswerView> loadAnswers(UUID applicationId) {
        return applicationAnswerRepository.findDetailByApplicationId(applicationId)
                .stream()
                .map(AnswerView::from)
                .toList();
    }

    public List<ApplicationListResponse> getMyApplications(UUID userId) {
        return applicationRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .stream()
                .map(ApplicationListResponse::from)
                .toList();
    }

    @Transactional
    public void cancel(UUID applicationId, UUID userId) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }

        if (application.getStatus() != ApplicationStatus.PENDING
                && application.getStatus() != ApplicationStatus.PAYMENT_PENDING) {
            throw new CustomException(ErrorCode.CANNOT_CANCEL);
        }

        application.cancel();

        // 우연한 식탁 회원 신청 취소 시 차감했던 이용권을 환불한다. (KAN-261)
        // 단, 회차가 이미 취소된 경우엔 회차 취소 시점에 일괄 환불되었으므로 중복 환불하지 않는다.
        if (application.getUser() != null
                && application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE
                && !application.isSessionCancelled()) {
            ticketService.refundOneTicket(application);
        }

        eventPublisher.publishEvent(new ApplicationCancelledEvent(application));
    }

    /** 회차별 정원을 차지한 인원(승인+출석). 신청이 없는 회차는 결과에 없다. (KAN-338) */
    public Map<UUID, Long> countSeatsBySessionIds(List<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return applicationRepository.countBySessionIdsGroupByStatus(sessionIds).stream()
                .filter(row -> ApplicationStatus.SEAT_OCCUPYING.contains(row.getStatus()))
                .collect(Collectors.groupingBy(ApplicationRepository.ApplicationSessionCountProjection::getSessionId,
                        Collectors.summingLong(ApplicationRepository.ApplicationSessionCountProjection::getCount)));
    }

    /** 이 회차들에 배정됐거나 희망 회차로 고른 활성 신청이 있는지. 회차 삭제 가드용. (KAN-338) */
    public boolean hasActiveApplications(List<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return false;
        }
        return applicationRepository.existsBySession_IdInAndDeletedAtIsNull(sessionIds)
                || applicationCandidateSessionRepository.existsBySession_IdInAndApplication_DeletedAtIsNull(sessionIds);
    }

    private String generateBookingNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return "WH" + date + "-" + suffix;
    }
}
