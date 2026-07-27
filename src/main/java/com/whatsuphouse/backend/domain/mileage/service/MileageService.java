package com.whatsuphouse.backend.domain.mileage.service;

import com.whatsuphouse.backend.domain.mileage.dto.response.MileageBalanceResponse;
import com.whatsuphouse.backend.domain.mileage.dto.response.MileageHistoryPageResponse;
import com.whatsuphouse.backend.domain.mileage.dto.response.MileageHistoryResponse;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.mileage.repository.MileageHistoryRepository;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MileageService {


    public static final int SIGNUP_REWARD_AMOUNT = 1000;
    public static final int ATTENDANCE_REWARD_AMOUNT = 1000;
    public static final int TEXT_REVIEW_REWARD_AMOUNT = 500;
    public static final int PHOTO_REVIEW_REWARD_AMOUNT = 1000;
    public static final int REVIEW_UPGRADE_REWARD_AMOUNT = 500;

    private final MileageHistoryRepository mileageHistoryRepository;
    private final UserRepository userRepository;

    public MileageHistory rewardSignup(User user) {
        return earn(user, MileageType.SIGNUP, SIGNUP_REWARD_AMOUNT, user.getId());
    }

    public MileageHistory rewardAttendance(User user, UUID applicationId) {
        return earn(user, MileageType.ATTENDANCE, ATTENDANCE_REWARD_AMOUNT, applicationId);
    }

    public MileageHistory rewardReview(User user, UUID reviewId, ReviewType reviewType) {
        int amount = reviewType == ReviewType.PHOTO ? PHOTO_REVIEW_REWARD_AMOUNT : TEXT_REVIEW_REWARD_AMOUNT;
        return earn(user, MileageType.REVIEW_REWARD, amount, reviewId);
    }

    public MileageHistory rewardReviewUpgrade(User user, UUID reviewId) {
        return earn(user, MileageType.REVIEW_UPGRADE, REVIEW_UPGRADE_REWARD_AMOUNT, reviewId);
    }

    public boolean rewardReviewUpgradeIfAbsent(User user, UUID reviewId) {
        if (mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, MileageType.REVIEW_UPGRADE, reviewId)) {
            return false;
        }

        earn(user, MileageType.REVIEW_UPGRADE, REVIEW_UPGRADE_REWARD_AMOUNT, reviewId);
        return true;
    }

    public MileageHistory earn(User user, MileageType type, int amount, UUID relatedId) {
        if (mileageHistoryRepository.existsByUserAndTypeAndRelatedId(user, type, relatedId)) {
            throw new CustomException(ErrorCode.MILEAGE_ALREADY_REWARDED);
        }

        Integer balanceAfter = user.addMileage(amount);
        MileageHistory history = MileageHistory.builder()
                .user(user)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .relatedId(relatedId)
                .build();

        return mileageHistoryRepository.save(history);
    }

    public MileageHistoryResponse adminAdjust(UUID userId, int amount, String adjustReason) {
        User user = findActiveUser(userId);

        if (amount == 0) {
            throw new CustomException(ErrorCode.MILEAGE_ADJUST_AMOUNT_ZERO);
        }

        if (amount < 0 && user.getMileageBalance() + amount < 0) {
            throw new CustomException(ErrorCode.MILEAGE_NOT_ENOUGH);
        }

        Integer balanceAfter = amount > 0 ? user.addMileage(amount) : user.deductMileage(-amount);

        MileageHistory history = MileageHistory.builder()
                .user(user)
                .type(MileageType.ADMIN_ADJUST)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .adjustReason(adjustReason)
                .build();

        return MileageHistoryResponse.from(mileageHistoryRepository.save(history));
    }

    @Transactional(readOnly = true)
    public MileageBalanceResponse getMyMileage(UUID userId) {
        User user = findActiveUser(userId);
        return MileageBalanceResponse.from(user);
    }

    @Transactional(readOnly = true)
    public MileageHistoryPageResponse getMyMileageHistory(UUID userId, MileageType type, int page, int size) {
        findActiveUser(userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "earnedDate"));
        Page<MileageHistory> historyPage = type == null
                ? mileageHistoryRepository.findByUserId(userId, pageable)
                : mileageHistoryRepository.findByUserIdAndType(userId, type, pageable);
        List<MileageHistoryResponse> content = historyPage.getContent().stream()
                .map(MileageHistoryResponse::from)
                .toList();

        return MileageHistoryPageResponse.from(new PageImpl<>(content, pageable, historyPage.getTotalElements()));
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
