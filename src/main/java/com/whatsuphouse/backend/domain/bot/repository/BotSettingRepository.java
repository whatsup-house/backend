package com.whatsuphouse.backend.domain.bot.repository;

import com.whatsuphouse.backend.domain.bot.entity.BotSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotSettingRepository extends JpaRepository<BotSetting, Short> {
}
