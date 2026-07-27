package com.whatsuphouse.backend.global.common;

import lombok.Getter;

@Getter
public class ApiResult<T> {

    private final boolean success;
    private final String message;
    private final T data;

    private ApiResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, "요청이 성공했습니다.", data);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(true, message, data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(false, message, null);
    }
}
