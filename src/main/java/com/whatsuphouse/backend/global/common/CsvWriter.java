package com.whatsuphouse.backend.global.common;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 4180 CSV. 엑셀에서 한글이 깨지지 않도록 UTF-8 BOM을 붙이고, 줄바꿈은 CRLF.
 * 사용자 입력이 섞이므로 =,+,-,@,탭,CR로 시작하는 셀은 앞에 작은따옴표를 붙여 수식으로 실행되지 않게 한다.
 */
public final class CsvWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private CsvWriter() {
    }

    /** 셀 값은 toString()으로 쓰고 null은 빈 칸. */
    public static byte[] write(List<String> header, List<? extends List<?>> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, header);
        rows.forEach(row -> appendRow(csv, row));
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    private static void appendRow(StringBuilder csv, List<?> cells) {
        csv.append(cells.stream().map(CsvWriter::escape).collect(Collectors.joining(","))).append("\r\n");
    }

    private static String escape(Object cell) {
        if (cell == null) {
            return "";
        }
        String value = cell.toString();
        if (!value.isEmpty() && FORMULA_PREFIXES.indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
