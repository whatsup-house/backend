package com.whatsuphouse.backend.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    @DisplayName("UTF-8 BOM을 붙이고 쉼표·따옴표·줄바꿈은 따옴표로 감싸며 수식 시작 셀은 무력화한다")
    void write_bomEscapingAndFormulaGuard() {
        byte[] csv = CsvWriter.write(List.of("이름", "메모"), List.of(
                Arrays.asList("홍,길동", "say \"hi\""),
                Arrays.asList("=HYPERLINK(\"x\")", null),
                Arrays.asList("줄\n바꿈", 3)));

        assertThat(Arrays.copyOf(csv, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8)).isEqualTo(
                "이름,메모\r\n"
                        + "\"홍,길동\",\"say \"\"hi\"\"\"\r\n"
                        + "\"'=HYPERLINK(\"\"x\"\")\",\r\n"
                        + "\"줄\n바꿈\",3\r\n");
    }
}
