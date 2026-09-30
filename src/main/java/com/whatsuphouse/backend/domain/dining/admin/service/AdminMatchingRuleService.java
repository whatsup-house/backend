package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.dining.admin.dto.request.MatchingRuleRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.MatchingRuleResponse;
import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import com.whatsuphouse.backend.domain.dining.repository.MatchingRuleSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 규칙 기본값(단일 행). 운영 DB는 V7이 기본 행을 넣는다.
 * Flyway를 끄는 local/test 프로필에서는 행이 없을 수 있어 조회는 기본값을, 수정은 기본 행을 만든 뒤 반영한다. (KAN-348)
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminMatchingRuleService {

    private final MatchingRuleSettingRepository matchingRuleSettingRepository;

    public MatchingRuleResponse getMatchingRules() {
        return MatchingRuleResponse.from(findMatchingRuleSetting());
    }

    /** 매칭 엔진이 쓰는 규칙 기본값 엔티티. 행이 없으면 기본값. */
    public MatchingRuleSetting findMatchingRuleSetting() {
        return matchingRuleSettingRepository.findById(MatchingRuleSetting.SINGLETON_ID)
                .orElseGet(MatchingRuleSetting::defaults);
    }

    @Transactional
    public MatchingRuleResponse updateMatchingRules(MatchingRuleRequest request) {
        MatchingRuleSetting setting = matchingRuleSettingRepository.findById(MatchingRuleSetting.SINGLETON_ID)
                .orElseGet(() -> matchingRuleSettingRepository.save(MatchingRuleSetting.defaults()));
        setting.update(request.getMaxAgeGap(), request.getTableSizeMin(), request.getTableSizeMax(),
                request.getMinGroupScore(), request.getAutoConfirmGraceMinutes(), request.getWeights().toWeights());
        return MatchingRuleResponse.from(setting);
    }
}
