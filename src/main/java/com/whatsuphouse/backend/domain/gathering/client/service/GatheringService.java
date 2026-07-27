package com.whatsuphouse.backend.domain.gathering.client.service;

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

    public List<GatheringResponse> getGatherings(LocalDate date, GatheringStatus status) {
        if (date != null && status != null) {
            return gatheringRepository.findByEventDateAndStatusAndDeletedAtIsNull(date, status)
                    .stream().map(GatheringResponse::from).toList();
        }
        if (date != null) {
            return gatheringRepository.findByEventDateAndDeletedAtIsNull(date)
                    .stream().map(GatheringResponse::from).toList();
        }
        if (status != null) {
            return gatheringRepository.findByStatusAndDeletedAtIsNull(status)
                    .stream().map(GatheringResponse::from).toList();
        }
        return gatheringRepository.findByDeletedAtIsNull()
                .stream().map(GatheringResponse::from).toList();
    }

    public GatheringDetailResponse getGathering(UUID id) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        return GatheringDetailResponse.from(gathering);
    }
}
