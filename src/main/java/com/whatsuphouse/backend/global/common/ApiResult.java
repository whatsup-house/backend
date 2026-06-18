package com.whatsuphouse.backend.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.Map;

@Getter
public class ApiResult<T> {

    private final boolean success;

    // 안정적인 에러/안내 코드. 프론트가 code → 다국어 메시지로 매핑한다. (KAN-265)
    // null이면 직렬화에서 생략되어 기존 성공 응답 형태는 그대로 유지된다(하위호환).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String code;

    // 메시지 내 동적 값(예: {"max": 20}). 프론트에서 보간한다. 없으면 생략된다. (KAN-265)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, Object> params;

    private final String message;
    private final T data;

    private ApiResult(boolean success, String code, Map<String, Object> params, String message, T data) {
        this.success = success;
        this.code = code;
        this.params = params;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, null, null, "요청이 성공했습니다.", data);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(true, null, null, message, data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(false, null, null, message, null);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(false, code, null, message, null);
    }

    public static <T> ApiResult<T> fail(String code, String message, Map<String, Object> params) {
        return new ApiResult<>(false, code, params, message, null);
    }
}
