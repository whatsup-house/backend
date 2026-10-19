package com.whatsuphouse.backend.domain.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 그록봇 전역 토글(관리자 화면). 1행 고정(id=1). (KAN-406) */
@Entity
@Table(name = "bot_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotSetting {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static BotSetting initial() {
        BotSetting setting = new BotSetting();
        setting.id = ID;
        setting.enabled = true;
        return setting;
    }

    public void change(boolean enabled, UUID updatedBy) {
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }
}
