package com.whatsuphouse.backend.domain.location.admin.service;

import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationCreateRequest;
import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationUpdateRequest;
import com.whatsuphouse.backend.domain.location.common.dto.response.LocationDetailResponse;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminLocationService {

    private final LocationRepository locationRepository;

    @Transactional
    public LocationDetailResponse createLocation(LocationCreateRequest request) {
        Location location = locationRepository.save(request.toEntity());
        return LocationDetailResponse.from(location);
    }

    @Transactional
    public LocationDetailResponse updateLocation(UUID id, LocationUpdateRequest request) {
        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        location.update(request.getName(), request.getAddress(), request.getMapUrl(),
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
