package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final GatheringRepository gatheringRepository;
    private final UserRepository userRepository;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public ApplicationResponse apply(UUID gatheringId, ApplicationRequest request, UUID userId) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        if (gathering.getStatus() != GatheringStatus.OPEN) {
            throw new CustomException(ErrorCode.GATHERING_NOT_RECRUITING);
        }

        int currentCount = applicationRepository.countByGatheringIdAndStatusNotAndDeletedAtIsNull(
                gathering.getId(), ApplicationStatus.CANCELLED);
        if (currentCount >= gathering.getMaxAttendees()) {
            throw new CustomException(ErrorCode.GATHERING_FULL);
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (applicationRepository.existsByGatheringIdAndUserIdAndDeletedAtIsNull(gathering.getId(), userId)) {
                throw new CustomException(ErrorCode.ALREADY_APPLIED);
            }
        } else {
            if (request.getPhone() == null || request.getPhone().isBlank()) {
                throw new CustomException(ErrorCode.GUEST_PHONE_REQUIRED);
            }
            if (applicationRepository.existsByGatheringIdAndPhoneAndDeletedAtIsNull(gathering.getId(), request.getPhone())) {
                throw new CustomException(ErrorCode.ALREADY_APPLIED);
            }
        }

        String name = (user != null) ? user.getName() : request.getName();
        String phone = (user != null) ? user.getPhone() : request.getPhone();

        Application application = Application.builder()
                .bookingNumber(generateBookingNumber())
                .gathering(gathering)
                .user(user)
                .name(name)
                .phone(phone)
                .gender(request.getGender())
                .age(request.getAge())
                .instagramId(request.getInstagramId())
                .job(request.getJob())
                .mbti(request.getMbti())
                .intro(request.getIntro())
                .referrerName(request.getReferrerName())
                .build();

        return ApplicationResponse.from(applicationRepository.save(application));
    }

    @Transactional
    public ApplicationResponse applyAsGuest(UUID gatheringId, ApplicationRequest request) {
        return apply(gatheringId, request, null);
    }

    public ApplicationCheckResponse checkApplication(String phone, String bookingNumber) {
        Application application = applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull(phone, bookingNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        return ApplicationCheckResponse.from(application);
    }

    public List<ApplicationListResponse> getMyApplications(UUID userId) {
        return applicationRepository.findByUserIdAndDeletedAtIsNull(userId)
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

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new CustomException(ErrorCode.CANNOT_CANCEL);
        }

        application.cancel();
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
