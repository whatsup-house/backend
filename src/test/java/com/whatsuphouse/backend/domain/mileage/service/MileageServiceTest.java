package com.whatsuphouse.backend.domain.mileage.service;

import com.whatsuphouse.backend.domain.mileage.dto.response.MileageBalanceResponse;
import com.whatsuphouse.backend.domain.mileage.dto.response.MileageHistoryPageResponse;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.mileage.repository.MileageHistoryRepository;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MileageServiceTest {

    @Mock
    private MileageHistoryRepository mileageHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MileageService mileageService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("홍길동")
                .gender(Gender.MALE)
                .age(25)
                .nickname("gildong")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Test
    @DisplayName("회원가입 마일리지를 지급하고 이력을 저장한다")
    void rewardSignup_success() {
        given(mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.SIGNUP, userId))
                .willReturn(false);
        given(mileageHistoryRepository.save(any(MileageHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mileageService.rewardSignup(user);

        ArgumentCaptor<MileageHistory> captor = ArgumentCaptor.forClass(MileageHistory.class);
        verify(mileageHistoryRepository).save(captor.capture());

        MileageHistory history = captor.getValue();
        assertThat(user.getMileageBalance()).isEqualTo(1000);
        assertThat(history.getUser()).isEqualTo(user);
        assertThat(history.getType()).isEqualTo(MileageType.SIGNUP);
        assertThat(history.getAmount()).isEqualTo(1000);
        assertThat(history.getBalanceAfter()).isEqualTo(1000);
        assertThat(history.getRelatedId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("이미 지급된 마일리지는 중복 지급하지 않는다")
    void rewardSignup_duplicate_throwsException() {
        given(mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.SIGNUP, userId))
                .willReturn(true);

        assertThatThrownBy(() -> mileageService.rewardSignup(user))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_ALREADY_REWARDED);

        assertThat(user.getMileageBalance()).isZero();
        verify(mileageHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("텍스트 리뷰 작성 마일리지는 500포인트를 지급한다")
    void rewardReview_text_success() {
        UUID reviewId = UUID.randomUUID();
        given(mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.REVIEW_REWARD, reviewId))
                .willReturn(false);
        given(mileageHistoryRepository.save(any(MileageHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mileageService.rewardReview(user, reviewId, ReviewType.TEXT);

        ArgumentCaptor<MileageHistory> captor = ArgumentCaptor.forClass(MileageHistory.class);
        verify(mileageHistoryRepository).save(captor.capture());

        MileageHistory history = captor.getValue();
        assertThat(user.getMileageBalance()).isEqualTo(500);
        assertThat(history.getType()).isEqualTo(MileageType.REVIEW_REWARD);
        assertThat(history.getAmount()).isEqualTo(500);
        assertThat(history.getBalanceAfter()).isEqualTo(500);
        assertThat(history.getRelatedId()).isEqualTo(reviewId);
    }

    @Test
    @DisplayName("사진 리뷰 작성 마일리지는 1000포인트를 지급한다")
    void rewardReview_photo_success() {
        UUID reviewId = UUID.randomUUID();
        given(mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.REVIEW_REWARD, reviewId))
                .willReturn(false);
        given(mileageHistoryRepository.save(any(MileageHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mileageService.rewardReview(user, reviewId, ReviewType.PHOTO);

        ArgumentCaptor<MileageHistory> captor = ArgumentCaptor.forClass(MileageHistory.class);
        verify(mileageHistoryRepository).save(captor.capture());

        MileageHistory history = captor.getValue();
        assertThat(user.getMileageBalance()).isEqualTo(1000);
        assertThat(history.getType()).isEqualTo(MileageType.REVIEW_REWARD);
        assertThat(history.getAmount()).isEqualTo(1000);
        assertThat(history.getBalanceAfter()).isEqualTo(1000);
        assertThat(history.getRelatedId()).isEqualTo(reviewId);
    }

    @Test
    @DisplayName("텍스트 리뷰를 사진 리뷰로 업그레이드하면 500포인트를 추가 지급한다")
    void rewardReviewUpgrade_success() {
        UUID reviewId = UUID.randomUUID();
        given(mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.REVIEW_UPGRADE, reviewId))
                .willReturn(false);
        given(mileageHistoryRepository.save(any(MileageHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mileageService.rewardReviewUpgrade(user, reviewId);

        ArgumentCaptor<MileageHistory> captor = ArgumentCaptor.forClass(MileageHistory.class);
        verify(mileageHistoryRepository).save(captor.capture());

        MileageHistory history = captor.getValue();
        assertThat(user.getMileageBalance()).isEqualTo(500);
        assertThat(history.getType()).isEqualTo(MileageType.REVIEW_UPGRADE);
        assertThat(history.getAmount()).isEqualTo(500);
        assertThat(history.getBalanceAfter()).isEqualTo(500);
        assertThat(history.getRelatedId()).isEqualTo(reviewId);
    }

    @Test
    @DisplayName("이미 업그레이드 마일리지가 지급된 리뷰는 추가 지급하지 않고 스킵한다")
    void rewardReviewUpgradeIfAbsent_duplicate_skipsReward() {
        UUID reviewId = UUID.randomUUID();
        given(mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.REVIEW_UPGRADE, reviewId))
                .willReturn(true);

        boolean rewarded = mileageService.rewardReviewUpgradeIfAbsent(user, reviewId);

        assertThat(rewarded).isFalse();
        assertThat(user.getMileageBalance()).isZero();
        verify(mileageHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 마일리지 잔액을 조회한다")
    void getMyMileage_success() {
        user.addMileage(1000);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        MileageBalanceResponse response = mileageService.getMyMileage(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getMileage()).isEqualTo(1000);
    }

    @Test
    @DisplayName("내 마일리지 이력을 최신순으로 조회한다")
    void getMyMileageHistory_success() {
        MileageHistory history = createHistory(MileageType.SIGNUP, 1000, 1000, userId);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(mileageHistoryRepository.findByUserId(any(UUID.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(history)));

        MileageHistoryPageResponse response = mileageService.getMyMileageHistory(userId, null, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getType()).isEqualTo(MileageType.SIGNUP);
        assertThat(response.getContent().get(0).getAmount()).isEqualTo(1000);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        verify(mileageHistoryRepository).findByUserId(any(UUID.class), any(Pageable.class));
    }

    @Test
    @DisplayName("마일리지 타입으로 이력을 필터링한다")
    void getMyMileageHistory_withType_success() {
        UUID reviewId = UUID.randomUUID();
        MileageHistory history = createHistory(MileageType.REVIEW_REWARD, 500, 1500, reviewId);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(mileageHistoryRepository.findByUserIdAndType(any(UUID.class), any(MileageType.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(history)));

        MileageHistoryPageResponse response = mileageService.getMyMileageHistory(userId, MileageType.REVIEW_REWARD, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getType()).isEqualTo(MileageType.REVIEW_REWARD);
        assertThat(response.getContent().get(0).getRelatedId()).isEqualTo(reviewId);
        verify(mileageHistoryRepository)
                .findByUserIdAndType(any(UUID.class), any(MileageType.class), any(Pageable.class));
    }

    private MileageHistory createHistory(MileageType type, int amount, int balanceAfter, UUID relatedId) {
        MileageHistory history = MileageHistory.builder()
                .user(user)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .relatedId(relatedId)
                .build();
        ReflectionTestUtils.setField(history, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(history, "earnedDate", LocalDateTime.now());
        return history;
    }
}
