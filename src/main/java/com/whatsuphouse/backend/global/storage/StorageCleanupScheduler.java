package com.whatsuphouse.backend.global.storage;

import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanupScheduler {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket}")
    private String bucket;

    private final StorageService storageService;
    private final RestClient restClient = RestClient.create();

    private static final List<String> TEMP_FOLDERS = List.of("carousel", "gathering", "review", "avatar");
    private static final long TEMP_FILE_TTL_HOURS = 24;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupTempFiles() {
        TEMP_FOLDERS.forEach(folder -> cleanupFolder("temp/" + folder + "/"));
    }

    private void cleanupFolder(String prefix) {
        try {
            List<Map<String, Object>> files = listFiles(prefix);
            Instant threshold = Instant.now().minus(TEMP_FILE_TTL_HOURS, ChronoUnit.HOURS);

            files.stream()
                    .filter(file -> isOlderThan(file, threshold))
                    .forEach(file -> {
                        String path = prefix + file.get("name");
                        storageService.delete(path);
                        log.info("temp 파일 삭제: {}", path);
                    });
        } catch (Exception e) {
            log.warn("temp 파일 정리 실패: prefix={}", prefix);
        }
    }

    private List<Map<String, Object>> listFiles(String prefix) {
        return restClient.post()
                .uri(supabaseUrl + "/storage/v1/object/list/" + bucket)
                .header("Authorization", "Bearer " + supabaseKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefix", prefix, "limit", 1000))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private boolean isOlderThan(Map<String, Object> file, Instant threshold) {
        try {
            String createdAt = (String) file.get("created_at");
            return Instant.parse(createdAt).isBefore(threshold);
        } catch (Exception e) {
            return false;
        }
    }
}
