package com.whatsuphouse.backend.global.storage.controller;

import com.whatsuphouse.backend.global.common.ApiResult;
import com.whatsuphouse.backend.global.storage.dto.ImageUploadResponse;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Image", description = "이미지 업로드 API")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final StorageService storageService;

    @Operation(summary = "이미지 업로드", description = "이미지를 임시 저장하고 tempPath를 반환합니다. 반환된 tempPath를 도메인 등록 시 사용하세요.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<ImageUploadResponse>> upload(
            @Parameter(description = "업로드할 이미지 파일 (jpg, jpeg, png, webp)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "저장 폴더 (carousel, gathering, review, avatar)")
            @RequestParam("folder") String folder
    ) {
        String tempPath = storageService.upload(file, folder);
        String previewUrl = storageService.getPublicUrl(tempPath);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("이미지가 업로드되었습니다.", new ImageUploadResponse(tempPath, previewUrl)));
    }
}
