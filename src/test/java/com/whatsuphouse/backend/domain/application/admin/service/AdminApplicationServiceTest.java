package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationDeleteResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AdminApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository applicationAnswerRepository;

    @Mock
    private MileageService mileageService;

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
                .user(user)
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private ApplicationStatusRequest buildStatusRequest(ApplicationStatus status) {
        return ApplicationStatusRequest.builder().status(status).build();
    }
}
