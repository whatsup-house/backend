package com.whatsuphouse.backend.global.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    // 메시지 보간용 파라미터(선택). 프론트가 code 매핑 후 보간한다. (KAN-265)
    private final Map<String, Object> params;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public CustomException(ErrorCode errorCode, Map<String, Object> params) {
        this.errorCode = errorCode;
        this.params = params;
    }

    @Override
    public String getMessage() {
        return errorCode.getMessage();
    }
}
