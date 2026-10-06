package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.matching.enums.AttendanceStatus;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DiningAttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private DiningAttendanceService diningAttendanceService;

    @Test
    @DisplayName("참가 횟수: 빈 입력이면 조회 없이 빈 Map (KAN-392)")
    void countAttendedByUserIds_empty() {
        assertThat(diningAttendanceService.countAttendedByUserIds(List.of())).isEmpty();
        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("참가 횟수: ATTENDED 집계 결과를 회원 ID → 건수 Map으로 돌려준다 (KAN-392)")
    void countAttendedByUserIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        given(attendanceRepository.countByUserIdsAndStatus(List.of(first, second, none), AttendanceStatus.ATTENDED))
                .willReturn(List.of(row(first, 3L), row(second, 1L)));

        assertThat(diningAttendanceService.countAttendedByUserIds(List.of(first, second, none)))
                .isEqualTo(Map.of(first, 3L, second, 1L));
    }

    private AttendanceRepository.UserCountProjection row(UUID userId, long count) {
        return new AttendanceRepository.UserCountProjection() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
