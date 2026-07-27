package com.whatsuphouse.backend.global.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageUploadResponse {

    @Schema(example = "temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
    private String tempPath;

    @Schema(example = "https://mcvtfdwsxmtqgxzlfqjxd.supabase.co/storage/v1/object/public/images/temp/carousel/550e8400-e29b-41d4-a716-446655440000.jpg")
    private String previewUrl;
}
