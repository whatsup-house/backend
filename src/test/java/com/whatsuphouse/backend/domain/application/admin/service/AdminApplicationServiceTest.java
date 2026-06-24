package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationPaymentRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationDeleteResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationPaymentResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

import com.whatsuphouse.backend.domain.notification.event.ApplicationAttendedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPaymentConfirmedEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository applicationAnswerRepository;

    @Mock
    private MileageService mileageService;

    @Mock
    private com.whatsuphouse.backend.domain.ticket.service.TicketService ticketService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminApplicationService adminApplicationService;

    private UUID gatheringId;
    private UUID applicationId;
    private Gathering gathering;
    private Application application;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(10)
                .build();
        ReflectionTestUtils.setField(gathering, "id", gatheringId);

        application = Application.builder()
                .bookingNumber("WH260428-ABC123")
                .gathering(gathering)
                .name("홍길동")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(application, "id", applicationId);

        lenient().when(applicationAnswerRepository.findByApplicationIds(any())).thenReturn(List.of());
        lenient().when(applicationAnswerRepository.findDetailByApplicationId(any())).thenReturn(List.of());
    }

    // ── getAllApplications() ──────────────────────────────────────────────────

    @Test
    @DisplayName("전체 신청 목록 반환")
    void getAllApplications_returnsList() {
        // GIVEN
        given(applicationRepository.findApplications(null, null)).willReturn(List.of(application));

        // WHEN
        List<AdminApplicationResponse> result = adminApplicationService.getAllApplications(null, null);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("신청이 없으면 빈 리스트 반환")
    void getAllApplications_empty_returnsEmptyList() {
        // GIVEN
        given(applicationRepository.findApplications(null, null)).willReturn(List.of());

        // WHEN
        List<AdminApplicationResponse> result = adminApplicationService.getAllApplications(null, null);

        // THEN
        assertThat(result).isEmpty();
    }

    // ── getApplicationsByGathering() ─────────────────────────────────────────

    @Test
    @DisplayName("게더링별 신청 목록 반환")
    void getApplicationsByGathering_returnsList() {
        // GIVEN
        given(applicationRepository.findApplications(gatheringId, null)).willReturn(List.of(application));

        // WHEN
        List<AdminApplicationResponse> result = adminApplicationService.getAllApplications(gatheringId, null);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGatheringId()).isEqualTo(gatheringId);
    }

    @Test
    @DisplayName("해당 게더링에 신청이 없으면 빈 리스트 반환")
    void getApplicationsByGathering_empty_returnsEmptyList() {
        // GIVEN
        given(applicationRepository.findApplications(gatheringId, null)).willReturn(List.of());

        // WHEN
        List<AdminApplicationResponse> result = adminApplicationService.getAllApplications(gatheringId, null);

        // THEN
        assertThat(result).isEmpty();
    }

    // ── getApplicationsByStatus() ────────────────────────────────────────────

    @Test
    @DisplayName("상태별 신청 목록 반환")
    void getApplicationsByStatus_returnsList() {
        // GIVEN
        given(applicationRepository.findApplications(null, ApplicationStatus.PENDING)).willReturn(List.of(application));

        // WHEN
        List<AdminApplicationResponse> result = adminApplicationService.getAllApplications(null, ApplicationStatus.PENDING);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("해당 상태의 신청이 없으면 빈 리스트 반환")
    void getApplicationsByStatus_empty_returnsEmptyList() {
        // GIVEN
        given(applicationRepository.findApplications(null, ApplicationStatus.CONFIRMED)).willReturn(List.of());

        // WHEN
        List<AdminApplicationResponse> result = adminApplicationService.getAllApplications(null, ApplicationStatus.CONFIRMED);

        // THEN
        assertThat(result).isEmpty();
    }

    // ── getApplication() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("신청 단건 조회 성공")
    void getApplication_success() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        // WHEN
        AdminApplicationResponse response = adminApplicationService.getApplication(applicationId);

        // THEN
        assertThat(response.getBookingNumber()).isEqualTo("WH260428-ABC123");
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("존재하지 않는 신청 조회 시 예외 발생")
    void getApplication_notFound_throwsException() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.empty());

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.getApplication(applicationId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    // ── changeStatus() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("CONFIRMED로 상태 변경 성공")
    void changeStatus_toConfirmed_success() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.CONFIRMED);

        // WHEN
        ApplicationStatusResponse response = adminApplicationService.changeStatus(applicationId, request);

        // THEN
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(response.getMileageRewarded()).isNull();
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("우연한 식탁 최초 승인 시 사람 자격을 승인하고 이용권이 없으면 결제 대기한다 (KAN-277)")
    void changeStatus_randomTableApprovalWithoutTicket_awaitsPayment() {
        Application randomTableApp = buildRandomTableApplication(buildMember(), GatheringStatus.OPEN);
        Participant participant = randomTableApp.getParticipant();
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(randomTableApp));
        given(ticketService.tryUseOneTicket(participant, randomTableApp)).willReturn(false);

        ApplicationStatusResponse response = adminApplicationService.changeStatus(
                applicationId, buildStatusRequest(ApplicationStatus.CONFIRMED));

        assertThat(participant.isApprovedForRandomTable()).isTrue();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        then(eventPublisher).should(never()).publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("우연한 식탁 승인 시 잔여 이용권이 있으면 즉시 확정한다 (KAN-277)")
    void changeStatus_randomTableApprovalWithTicket_confirms() {
        Application randomTableApp = buildRandomTableApplication(buildMember(), GatheringStatus.OPEN);
        Participant participant = randomTableApp.getParticipant();
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(randomTableApp));
        given(ticketService.tryUseOneTicket(participant, randomTableApp)).willReturn(true);

        ApplicationStatusResponse response = adminApplicationService.changeStatus(
                applicationId, buildStatusRequest(ApplicationStatus.CONFIRMED));

        assertThat(participant.isApprovedForRandomTable()).isTrue();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("무료 우연한 식탁 승인 시 이용권 차감 없이 즉시 확정한다")
    void changeStatus_freeRandomTableApproval_confirmsWithoutTicket() {
        Application randomTableApp = buildRandomTableApplication(buildMember(), GatheringStatus.OPEN);
        ReflectionTestUtils.setField(randomTableApp.getGathering(), "price", 0);
        Participant participant = randomTableApp.getParticipant();
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(randomTableApp));

        ApplicationStatusResponse response = adminApplicationService.changeStatus(
                applicationId, buildStatusRequest(ApplicationStatus.CONFIRMED));

        assertThat(participant.isApprovedForRandomTable()).isTrue();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(ticketService).should(never()).tryUseOneTicket(participant, randomTableApp);
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("우연한 식탁 거절은 사람 자격과 신청에 함께 반영한다 (KAN-277)")
    void changeStatus_randomTableRejection_rejectsParticipantAndApplication() {
        Application randomTableApp = buildRandomTableApplication(buildMember(), GatheringStatus.OPEN);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(randomTableApp));
        ApplicationStatusRequest request = ApplicationStatusRequest.builder()
                .status(ApplicationStatus.REJECTED)
                .rejectionReason("운영 기준에 맞지 않음")
                .build();

        ApplicationStatusResponse response = adminApplicationService.changeStatus(applicationId, request);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(randomTableApp.getRejectionReason()).isEqualTo("운영 기준에 맞지 않음");
        assertThat(randomTableApp.getParticipant().isRandomTableEligibilityRestricted()).isTrue();
    }

    @Test
    @DisplayName("거절 사유 없이 거절할 수 없다 (KAN-277)")
    void changeStatus_rejectionWithoutReason_throws() {
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> adminApplicationService.changeStatus(
                applicationId, buildStatusRequest(ApplicationStatus.REJECTED)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REJECTION_REASON_REQUIRED);
    }

    @Test
    @DisplayName("확정 시 이미 정원(CONFIRMED+ATTENDED)이 가득 차 있으면 GATHERING_FULL 예외 (KAN-236)")
    void changeStatus_toConfirmed_capacityFull_throwsException() {
        // GIVEN: 확정/출석 인원이 이미 정원(10)에 도달
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(
                gatheringId, ApplicationStatus.SEAT_OCCUPYING)).willReturn(10);
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.CONFIRMED);

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changeStatus(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_FULL);
    }

    @Test
    @DisplayName("PENDING을 CONFIRMED 없이 바로 ATTENDED로 처리할 때도 정원이 가득 차 있으면 GATHERING_FULL (KAN-236)")
    void changeStatus_pendingToAttended_capacityFull_throwsException() {
        // GIVEN: PENDING 신청을 바로 출석 처리하려 하지만 좌석이 이미 가득 참
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(
                gatheringId, ApplicationStatus.SEAT_OCCUPYING)).willReturn(10);
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.ATTENDED);

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changeStatus(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_FULL);
    }

    @Test
    @DisplayName("CANCELLED로 상태 변경 시도 시 예외 발생")
    void changeStatus_toCancelled_throwsException() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.CANCELLED);

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changeStatus(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("ATTENDED로 상태 변경 성공 - 게스트 신청이면 마일리지 미지급")
    void changeStatus_toAttended_guest_noMileage() {
        // GIVEN — application.user == null (게스트)
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.ATTENDED);

        // WHEN
        ApplicationStatusResponse response = adminApplicationService.changeStatus(applicationId, request);

        // THEN
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.ATTENDED);
        assertThat(response.getMileageRewarded()).isNull();
        assertThat(response.getUserMileageAfter()).isNull();
    }

    @Test
    @DisplayName("ATTENDED로 상태 변경 성공 - 회원 신청이면 1000 마일리지 지급")
    void changeStatus_toAttended_member_mileageRewarded() {
        // GIVEN
        User user = User.builder()
                .email("test@test.com").password("pw").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        Application memberApplication = Application.builder()
                .bookingNumber("WH260428-XYZ999")
                .gathering(gathering)
                .participant(Participant.member(user))
                .name("홍길동")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(memberApplication, "id", applicationId);

        MileageHistory history = MileageHistory.builder()
                .user(user).type(MileageType.ATTENDANCE).amount(1000).balanceAfter(1000)
                .relatedId(applicationId).build();

        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(memberApplication));
        given(mileageService.rewardAttendance(user, applicationId)).willReturn(history);
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.ATTENDED);

        // WHEN
        ApplicationStatusResponse response = adminApplicationService.changeStatus(applicationId, request);

        // THEN
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.ATTENDED);
        assertThat(response.getMileageRewarded()).isEqualTo(1000);
        assertThat(response.getUserMileageAfter()).isEqualTo(1000);
        then(eventPublisher).should().publishEvent(any(ApplicationAttendedEvent.class));
    }

    @Test
    @DisplayName("이미 ATTENDED 상태인 신청에 ATTENDED 요청 시 409 예외 발생")
    void changeStatus_alreadyAttended_throwsConflict() {
        // GIVEN
        application.attend();
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.ATTENDED);

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changeStatus(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ATTENDED);
    }

    @Test
    @DisplayName("PENDING으로 변경 시도 시 예외 발생")
    void changeStatus_toPending_throwsException() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.PENDING);

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changeStatus(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("존재하지 않는 신청 상태 변경 시 예외 발생")
    void changeStatus_notFound_throwsException() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.empty());
        ApplicationStatusRequest request = buildStatusRequest(ApplicationStatus.CONFIRMED);

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changeStatus(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    // ── deleteApplication() ──────────────────────────────────────────────────

    @Test
    @DisplayName("PENDING 신청 삭제 성공")
    void deleteApplication_pending_success() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        //WHEN
        ApplicationDeleteResponse response = adminApplicationService.deleteApplication(applicationId);
        // THEN
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 CANCELLED인 신청은 멱등 처리")
    void deleteApplication_alreadyCancelled_idempotent() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        application.cancel();

        //WHEN
        ApplicationDeleteResponse response = adminApplicationService.deleteApplication(applicationId);
        // THEN
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    @Test
    @DisplayName("ATTENDED 신청 삭제 시도 시 예외 발생")
    void deleteApplication_attended_throwsException() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        application.attend();

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.deleteApplication(applicationId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_DELETE);
    }

    @Test
    @DisplayName("우연한 식탁 회원 신청을 관리자가 삭제하면 이용권을 환불한다 (KAN-261)")
    void deleteApplication_randomTableMember_refundsTicket() {
        // GIVEN
        User member = buildMember();
        Application randomTableApp = buildRandomTableApplication(member, GatheringStatus.OPEN);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(randomTableApp));

        // WHEN
        adminApplicationService.deleteApplication(applicationId);

        // THEN
        then(ticketService).should().refundOneTicket(randomTableApp);
    }

    @Test
    @DisplayName("게더링이 이미 취소된 경우엔 개별 삭제 시 이용권을 중복 환불하지 않는다 (KAN-261)")
    void deleteApplication_cancelledGathering_skipsRefund() {
        // GIVEN
        User member = buildMember();
        Application randomTableApp = buildRandomTableApplication(member, GatheringStatus.CANCELLED);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(randomTableApp));

        // WHEN
        adminApplicationService.deleteApplication(applicationId);

        // THEN
        then(ticketService).should(never()).refundOneTicket(any(Application.class));
    }

    private User buildMember() {
        User member = User.builder()
                .email("member@example.com")
                .password("encoded")
                .name("김회원")
                .gender(Gender.FEMALE)
                .age(28)
                .nickname("member1")
                .phone("01099998888")
                .build();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        return member;
    }

    private Application buildRandomTableApplication(User member, GatheringStatus status) {
        Gathering randomTable = Gathering.builder()
                .title("우연한 식탁")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(8)
                .gatheringType(GatheringType.RANDOM_TABLE)
                .build();
        ReflectionTestUtils.setField(randomTable, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(randomTable, "status", status);

        Application app = Application.builder()
                .bookingNumber("WH260618-RT0001")
                .gathering(randomTable)
                .participant(Participant.member(member))
                .name(member.getName())
                .phone(member.getPhone())
                .build();
        ReflectionTestUtils.setField(app, "id", applicationId);
        return app;
    }

    // ── changePayment() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("입금 확인 처리 시 입금 완료 상태가 된다 (KAN-242)")
    void changePayment_confirm_success() {
        // GIVEN — 유료 게더링
        ReflectionTestUtils.setField(gathering, "price", 10000);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationPaymentRequest request = ApplicationPaymentRequest.builder().confirmed(true).build();

        // WHEN
        ApplicationPaymentResponse response = adminApplicationService.changePayment(applicationId, request);

        // THEN
        assertThat(response.isPaid()).isTrue();
        assertThat(response.isPaymentConfirmed()).isTrue();
        assertThat(response.getPaymentConfirmedAt()).isNotNull();
        // 입금 확인 중 → 완료로 처음 넘어가면 입금 완료 안내 이벤트 발행
        then(eventPublisher).should().publishEvent(any(ApplicationPaymentConfirmedEvent.class));
    }

    @Test
    @DisplayName("이미 입금 확인된 신청을 재확인하면 이벤트를 발행하지 않는다 (중복 방지)")
    void changePayment_reconfirm_doesNotPublishEvent() {
        // GIVEN — 이미 입금 확인된 신청
        ReflectionTestUtils.setField(gathering, "price", 10000);
        application.confirmPayment();
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationPaymentRequest request = ApplicationPaymentRequest.builder().confirmed(true).build();

        // WHEN
        adminApplicationService.changePayment(applicationId, request);

        // THEN
        then(eventPublisher).should(never()).publishEvent(any(ApplicationPaymentConfirmedEvent.class));
    }

    @Test
    @DisplayName("입금 확인 해제 시 입금 확인 중 상태로 돌아간다 (KAN-242)")
    void changePayment_cancel_success() {
        // GIVEN — 이미 입금 확인된 신청
        ReflectionTestUtils.setField(gathering, "price", 10000);
        application.confirmPayment();
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationPaymentRequest request = ApplicationPaymentRequest.builder().confirmed(false).build();

        // WHEN
        ApplicationPaymentResponse response = adminApplicationService.changePayment(applicationId, request);

        // THEN
        assertThat(response.isPaymentConfirmed()).isFalse();
        assertThat(response.getPaymentConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("입금 확인은 신청 상태를 변경하지 않는다 (독립 토글)")
    void changePayment_doesNotChangeApplicationStatus() {
        // GIVEN
        ReflectionTestUtils.setField(gathering, "price", 10000);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));
        ApplicationPaymentRequest request = ApplicationPaymentRequest.builder().confirmed(true).build();

        // WHEN
        adminApplicationService.changePayment(applicationId, request);

        // THEN — 입금 확인이 확정을 자동 트리거하지 않음
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("존재하지 않는 신청 입금 확인 시 예외 발생")
    void changePayment_notFound_throwsException() {
        // GIVEN
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.empty());
        ApplicationPaymentRequest request = ApplicationPaymentRequest.builder().confirmed(true).build();

        // WHEN & THEN
        assertThatThrownBy(() -> adminApplicationService.changePayment(applicationId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ApplicationStatusRequest buildStatusRequest(ApplicationStatus status) {
        return ApplicationStatusRequest.builder().status(status).build();
    }
}
