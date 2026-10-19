package com.whatsuphouse.backend.domain.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.domain.user.dto.response.ProfileResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileResponseTest {

    @Test
    @DisplayName("내 정보 응답 JSON에 isDemo, depositRequired가 그대로 노출된다")
    void from_exposesDemoAndDepositRequired() throws Exception {
        User user = User.builder().email("a@b.c").password("x").name("홍길동").gender(Gender.MALE)
                .age(30).nickname("gd").build();
        user.markDemo();
        user.markDepositRequired();

        JsonNode json = new ObjectMapper().valueToTree(ProfileResponse.from(user, null));

        assertThat(json.get("isDemo").asBoolean()).isTrue();
        assertThat(json.get("depositRequired").asBoolean()).isTrue();
        assertThat(json.has("demo")).isFalse();
    }
}
