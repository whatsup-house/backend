package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.AnswerItem;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationApprovedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPendingEvent;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.service.ParticipantService;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
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
    private final GatheringRepository gatheringRepository;
    private final UserRepository userRepository;
    private final FormRepository formRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FormProvisionService formProvisionService;
    private final TicketService ticketService;
    private final ParticipantService participantService;
    private final AuthService authService;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Transactional
    public ApplicationResponse apply(UUID gatheringId, ApplicationRequest request, UUID userId) {
        return applyInternal(gatheringId, request, userId);
    }

    @Transactional
    public ApplicationResponse applyAsGuest(UUID gatheringId, ApplicationRequest request) {
        return applyInternal(gatheringId, request, null);
    }

    private ApplicationResponse applyInternal(UUID gatheringId, ApplicationRequest request, UUID userId) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        // eventDate가 지난 모집중 게더링은 effective status가 COMPLETED로 계산되어 신청이 차단된다. (KAN-163)
        if (gathering.getEffectiveStatus() != GatheringStatus.OPEN) {
            throw new CustomException(ErrorCode.GATHERING_NOT_RECRUITING);
        }

        // 정원은 관리자가 승인(CONFIRMED)·출석(ATTENDED) 처리한 인원만 차지한다. PENDING 신청은 정원과 무관. (KAN-236)
        int occupiedSeats = applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(
                gathering.getId(), ApplicationStatus.SEAT_OCCUPYING);
        if (occupiedSeats >= gathering.getMaxAttendees()) {
            throw new CustomException(ErrorCode.GATHERING_FULL);
        }

        // 폼이 없는 게더링(시드/레거시)도 신청 가능하도록 기본 폼을 프로비저닝한다. (KAN-206)
        Form form = formRepository
                .findByGathering_IdAndDeletedAtIsNull(gatheringId)
                .orElseGet(() -> formProvisionService.createDefaultForm(gathering));

        List<FormQuestion> questions = formQuestionRepository
                .findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(form);

        Map<UUID, FormQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(FormQuestion::getId, q -> q));

        // 회원은 이름/연락처를 계정에서 가져오므로 시스템 예약 질문은 필수 검증에서 제외한다.
        validateAnswers(request.getAnswers(), questions, questionMap, userId != null);

        // questionKey → value 맵
        Map<String, Object> byKey = buildAnswersByKey(request.getAnswers(), questionMap);

        User user = null;
        if (userId != null) {
            user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(gathering.getId(), userId)) {
                throw new CustomException(ErrorCode.ALREADY_APPLIED);
            }
        } else {
            String guestPhone = extractString(byKey, "phone");
            if (guestPhone == null || guestPhone.isBlank()) {
                throw new CustomException(ErrorCode.GUEST_PHONE_REQUIRED);
            }
            if (applicationRepository.existsByGatheringIdAndPhoneAndDeletedAtIsNull(gathering.getId(), guestPhone)) {
                throw new CustomException(ErrorCode.ALREADY_APPLIED);
            }
            // 이메일 누락은 시스템 예약 질문(required)에서 REQUIRED_ANSWER_MISSING으로 처리된다.
            // 여기서는 값이 있을 때 형식만 검증한다. (폼에 email 질문이 없는 구버전은 통과)
            String guestEmail = extractString(byKey, "email");
            if (guestEmail != null && !guestEmail.isBlank() && !EMAIL_PATTERN.matcher(guestEmail).matches()) {
                throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
            }
            if (guestEmail == null || guestEmail.isBlank() || !authService.isGuestEmailVerified(guestEmail)) {
                throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
            }
        }

        String name = user != null ? user.getName() : extractString(byKey, "name");
        String phone = user != null ? user.getPhone() : extractString(byKey, "phone");
        // 알림 발송용 이메일: 회원=계정 이메일, 비회원=신청서 답변 이메일
        String email = user != null ? user.getEmail() : extractString(byKey, "email");

        // 신청 주체를 participant로 연결한다. 회원은 user당 1개 보장, 비회원은 신청마다 새 GUEST. (KAN-276)
        Participant participant = user != null
                ? participantService.getOrCreateForUser(user)
                : participantService.getOrCreateVerifiedGuest(name, email, phone);

        boolean autoConfirmed = false;
        boolean paymentPending = false;
        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
            validateRandomTableEligibility(participant);
        }

        Application application = Application.builder()
                .bookingNumber(generateBookingNumber())
                .gathering(gathering)
                .participant(participant)
                .name(name)
                .phone(phone)
                .email(email)
                .formSnapshot(buildFormSnapshot(questions))
                .build();

        Application saved = applicationRepository.save(application);
        if (user == null) {
            authService.consumeGuestEmailVerification(email);
        }

        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE
                && participant.isApprovedForRandomTable()) {
            autoConfirmed = ticketService.tryUseOneTicket(participant, saved);
            paymentPending = !autoConfirmed;
            if (autoConfirmed) {
                saved.confirm();
            } else {
                saved.awaitPayment();
            }
        }

        saveAnswers(saved, request.getAnswers(), questionMap);

        if (autoConfirmed) {
            eventPublisher.publishEvent(new ApplicationConfirmedEvent(saved));
        } else if (paymentPending) {
            eventPublisher.publishEvent(new ApplicationApprovedEvent(saved));
        } else {
            eventPublisher.publishEvent(new ApplicationPendingEvent(saved));
        }
        return ApplicationResponse.from(saved);
    }

    private void validateRandomTableEligibility(Participant participant) {
        if (participant.isAccountBlocked()) {
            throw new CustomException(ErrorCode.PARTICIPANT_BLOCKED);
        }
        if (participant.isRandomTableEligibilityRestricted()) {
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

        for (FormQuestion q : questions) {
            if (skipReservedRequired && q.isSystemReserved()) {
                continue;
            }
            if (q.isRequired() && !submittedIds.contains(q.getId())) {
                throw new CustomException(ErrorCode.REQUIRED_ANSWER_MISSING);
            }
        }
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
        return ApplicationCheckResponse.from(application, loadAnswers(application.getId()));
    }

    public ApplicationCheckResponse getMyApplication(UUID applicationId, UUID userId) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }
        return ApplicationCheckResponse.from(application, loadAnswers(applicationId));
    }

    private List<AnswerView> loadAnswers(UUID applicationId) {
        return applicationAnswerRepository.findDetailByApplicationId(applicationId)
                .stream()
                .map(AnswerView::from)
                .toList();
    }

    public List<ApplicationListResponse> getMyApplications(UUID userId) {
        return applicationRepository.findByParticipant_User_IdAndDeletedAtIsNull(userId)
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
        // 단, 게더링이 이미 취소된 경우엔 게더링 취소 시점에 일괄 환불되었으므로 중복 환불하지 않는다.
        if (application.getUser() != null
                && application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE
                && application.getGathering().getStatus() != GatheringStatus.CANCELLED) {
            ticketService.refundOneTicket(application);
        }

        eventPublisher.publishEvent(new ApplicationCancelledEvent(application));
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
