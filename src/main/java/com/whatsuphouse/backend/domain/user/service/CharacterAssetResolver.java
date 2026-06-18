package com.whatsuphouse.backend.domain.user.service;

import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.enums.JobCategory;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 직업 코드로부터 캐릭터 PNG의 Supabase 공개 URL을 만든다. (KAN-271)
 * 우선순위: 세부직업 전용 → 직업군 → 정장 기본(DEFAULT_SUIT).
 * 실제 에셋은 추후 업로드되며, 미존재 시 프론트가 onError로 폴백을 처리한다.
 */
@Component
@RequiredArgsConstructor
public class CharacterAssetResolver {

    private static final String CHARACTER_DIR = "characters/";
    private static final String DEFAULT_SUIT_PATH = CHARACTER_DIR + "DEFAULT_SUIT.png";

    // 세부직업 전용 캐릭터가 제작되면 해당 코드를 등록한다(확장 지점). 등록 전까지는 직업군 캐릭터를 사용한다.
    private static final Set<String> JOBS_WITH_DEDICATED_CHARACTER = Set.of();

    private final StorageService storageService;

    public String resolve(String jobCode) {
        Job job = Job.fromCode(jobCode).orElse(null);
        if (job == null || job.getCategory() == JobCategory.ETC) {
            return storageService.getPublicUrl(DEFAULT_SUIT_PATH);
        }
        if (JOBS_WITH_DEDICATED_CHARACTER.contains(job.name())) {
            return storageService.getPublicUrl(CHARACTER_DIR + "jobs/" + job.name() + ".png");
        }
        return storageService.getPublicUrl(CHARACTER_DIR + job.getCategory().name() + ".png");
    }
}
