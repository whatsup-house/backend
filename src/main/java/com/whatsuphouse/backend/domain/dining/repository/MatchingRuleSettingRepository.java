package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRuleSettingRepository extends JpaRepository<MatchingRuleSetting, Integer> {
}
