package com.whatsuphouse.backend.domain.location.admin.service;

import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationCreateRequest;
import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationUpdateRequest;
import com.whatsuphouse.backend.domain.location.admin.dto.response.AdminLocationResponse;
import com.whatsuphouse.backend.domain.location.common.dto.response.LocationDetailResponse;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminLocationService {

    private final LocationRepository locationRepository;

    // 관리자 화면용 목록 — 공개 응답에 없는 운영 필드(수용 인원/계약 상태/메모)를 포함한다. (KAN-208)
    public List<AdminLocationResponse> getLocations() {
        return locationRepository.findByDeletedAtIsNull().stream()
                .map(AdminLocationResponse::from)
                .toList();
    }

    @Transactional
    public LocationDetailResponse createLocation(LocationCreateRequest request) {
        Location location = locationRepository.save(request.toEntity());
        return LocationDetailResponse.from(location);
    }

    @Transactional
    public LocationDetailResponse updateLocation(UUID id, LocationUpdateRequest request) {
        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        location.update(request.getName(), request.getAddress(),
                request.getNaverMapUrl(), request.getKakaoMapUrl(),
                request.getMaxCapacity(), request.getStatus(), request.getMemo());
        return LocationDetailResponse.from(location);
    }

    @Transactional
    public void deleteLocation(UUID id) {
        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        location.delete();
    }
}
