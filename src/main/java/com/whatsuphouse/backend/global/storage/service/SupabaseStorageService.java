package com.whatsuphouse.backend.global.storage.service;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SupabaseStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    // 업로드 가능한 folder는 화이트리스트로만 허용한다. (경로 조작 방지)
    private static final Set<String> ALLOWED_FOLDERS = Set.of("carousel", "gathering", "review", "avatar");
    private static final MediaType WEBP_MEDIA_TYPE = MediaType.parseMediaType("image/webp");
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PATH_SEPARATOR = "/";

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket}")
    private String bucket;

    private final RestClient restClient = RestClient.create();

    @Override
    public String upload(MultipartFile file, String folder) {
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FOLDER);
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FORMAT);
        }

        byte[] bytes = readBytes(file);
        // 확장자·전달된 Content-Type을 신뢰하지 않고, 실제 파일 내용(매직바이트)으로 이미지 형식을 판별한다.
        MediaType detectedType = detectImageContentType(bytes);

        String fileName = UUID.randomUUID() + "." + extension;
        String tempPath = String.join(PATH_SEPARATOR, "temp", folder, fileName);

        try {
            restClient.put()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + tempPath)
                    .header(AUTHORIZATION, BEARER_PREFIX + supabaseKey)
                    .contentType(detectedType)
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[Storage] upload Exception: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return tempPath;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("[Storage] 파일 읽기 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    /**
     * 파일 앞부분 매직바이트로 실제 이미지 형식을 판별한다.
     * 클라이언트가 보낸 확장자/Content-Type을 신뢰하지 않기 위함이다.
     * 허용 형식이 아니면 INVALID_IMAGE_FORMAT 예외를 던진다.
     *
     * 주의: 매직바이트 검사는 "형식 위장"을 차단할 뿐 콘텐츠 무해성을 보장하지 않는다
     * (예: 유효한 이미지 헤더 뒤에 페이로드를 붙인 폴리글랏). 다만 업로드물은 판별된
     * Content-Type으로 고정 저장되고 정적 에셋(공개 URL)으로만 서빙되어 서버 측 해석
     * 경로가 없으므로 현재 아키텍처에서 즉시 실행 위험은 낮다.
     */
    MediaType detectImageContentType(byte[] bytes) {
        if (bytes != null && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        if (bytes != null && bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return MediaType.IMAGE_PNG;
        }
        // RIFF 컨테이너 + WEBP fourcc + VP8 코덱 헤더(VP8 / VP8L / VP8X)까지 확인해
        // WAVE 등 비이미지 RIFF가 webp로 위장 통과하는 것을 막는다.
        if (bytes != null && bytes.length >= 16
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P'
                && bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8') {
            return WEBP_MEDIA_TYPE;
        }
        throw new CustomException(ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Override
    public String move(String tempPath, String targetFolder) {
        String fileName = tempPath.substring(tempPath.lastIndexOf(PATH_SEPARATOR) + 1);
        String destinationKey = String.join(PATH_SEPARATOR, targetFolder, fileName);

        try {
            restClient.post()
                    .uri(supabaseUrl + "/storage/v1/object/move")
                    .header(AUTHORIZATION, BEARER_PREFIX + supabaseKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "bucketId", bucket,
                            "sourceKey", tempPath,
                            "destinationKey", destinationKey
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[Storage] move Exception: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return getPublicUrl(destinationKey);
    }

    @Override
    public String getPublicUrl(String path) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    @Override
    public void delete(String path) {
        try {
            restClient.delete()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + path)
                    .header(AUTHORIZATION, BEARER_PREFIX + supabaseKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
