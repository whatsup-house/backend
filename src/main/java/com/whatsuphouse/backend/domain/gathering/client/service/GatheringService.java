package com.whatsuphouse.backend.domain.gathering.client.service;

import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GatheringService {

    private final GatheringRepository gatheringRepository;

    public List<GatheringResponse> listGatherings(LocalDate date, GatheringStatus status) {
        return findGatherings(date, status).stream()
                .filter(gathering -> isVisibleInList(gathering, status))
                .map(GatheringResponse::from)
                .toList();
    }

    private List<Gathering> findGatherings(LocalDate date, GatheringStatus status) {
        if (date != null && status != null) {
            return gatheringRepository.findByEventDateAndStatusAndDeletedAtIsNull(date, status);
        }
        if (date != null) {
            return gatheringRepository.findByEventDateAndDeletedAtIsNull(date);
        }
        if (status != null) {
            return gatheringRepository.findByStatusAndDeletedAtIsNull(status);
        }
        return gatheringRepository.findByDeletedAtIsNull();
    }

    // status=OPEN(모집중) 조회 시 eventDate가 지난 게더링은 모집 목록에서 제외한다. (KAN-163)
    private boolean isVisibleInList(Gathering gathering, GatheringStatus status) {
        if (status == GatheringStatus.OPEN) {
            return !gathering.getEventDate().isBefore(LocalDate.now());
        }
        return true;
    }

    public List<CuratedGatheringResponse> listCuratedGatherings() {
        return gatheringRepository.findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc()
                .stream().map(CuratedGatheringResponse::from).toList();
    }

    public GatheringDetailResponse getGathering(UUID id) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        return GatheringDetailResponse.from(gathering);
    }
}
