package com.whatsuphouse.backend.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepositTextParserTest {

    private static final String SAMPLE = "[입금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30";

    @Test
    @DisplayName("우리WON 입금 푸시 원문에서 입금자명과 금액을 뽑는다(이름 뒤 공백 가변)")
    void parse_wooriPush_extractsNameAndAmount() {
        // given & when
        var parsed = DepositTextParser.parse(SAMPLE);

        // then
        assertThat(parsed).isPresent();
        assertThat(parsed.get().name()).isEqualTo("한지형");
        assertThat(parsed.get().amount()).isEqualTo(40_000);
        assertThat(DepositTextParser.isDepositAlert(SAMPLE)).isTrue();
    }

    @Test
    @DisplayName("[출금] 알림은 입금 알림이 아니다")
    void parse_withdrawal_notDeposit() {
        // given
        String raw = "[출금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30";

        // when & then
        assertThat(DepositTextParser.isDepositAlert(raw)).isFalse();
        assertThat(DepositTextParser.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("입금 알림이지만 형식이 깨졌으면 empty (호출부가 파싱 실패로 처리)")
    void parse_broken_empty() {
        // given
        String raw = "[입금] 한지형 사십만원";

        // when & then
        assertThat(DepositTextParser.isDepositAlert(raw)).isTrue();
        assertThat(DepositTextParser.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("입금자명이 비면(공백만) 파싱 실패 — 빈 이름은 모든 후보에 앞부분 일치하므로")
    void parse_blankName_empty() {
        // given
        String raw = "[입금]   9,900원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30";

        // when & then
        assertThat(DepositTextParser.isDepositAlert(raw)).isTrue();
        assertThat(DepositTextParser.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("int 범위를 넘는 금액은 파싱 실패")
    void parse_hugeAmount_empty() {
        assertThat(DepositTextParser.parse("[입금] 한지형 99,999,999,999원 1002")).isEmpty();
    }
}
