package com.whatsuphouse.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // User
    USER_NOT_FOUND("존재하지 않는 유저입니다.", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    INVALID_PASSWORD("비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),

    // Auth
    UNAUTHORIZED("인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_REFRESH_TOKEN("만료된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),

    // User
    DUPLICATE_NICKNAME("이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    INVALID_PAGE_SIZE("페이지 사이즈는 1 이상 100 이하여야 합니다.", HttpStatus.BAD_REQUEST),

    // Gathering
    GATHERING_NOT_FOUND("존재하지 않는 게더링입니다.", HttpStatus.NOT_FOUND),
    GATHERING_FULL("게더링 정원이 초과되었습니다.", HttpStatus.BAD_REQUEST),
    GATHERING_NOT_RECRUITING("모집중인 게더링이 아닙니다.", HttpStatus.BAD_REQUEST),

    // Form
    FORM_NOT_FOUND("신청폼이 존재하지 않습니다.", HttpStatus.NOT_FOUND),

    // Application
    APPLICATION_NOT_FOUND("존재하지 않는 신청입니다.", HttpStatus.NOT_FOUND),
    ALREADY_APPLIED("이미 신청한 게더링입니다.", HttpStatus.CONFLICT),
    CANNOT_CANCEL("취소할 수 없는 신청입니다.", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION("허용되지 않는 상태 변경입니다.", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE("출석 완료된 신청은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST),
    APPLICATION_FORBIDDEN("본인의 신청이 아닙니다.", HttpStatus.FORBIDDEN),
    GUEST_PHONE_REQUIRED("비회원 신청 시 전화번호는 필수입니다.", HttpStatus.BAD_REQUEST),
    ALREADY_ATTENDED("이미 출석 처리된 신청입니다.", HttpStatus.CONFLICT),

    // Location
    LOCATION_NOT_FOUND("존재하지 않는 장소입니다.", HttpStatus.NOT_FOUND),

    // Review
    REVIEW_NOT_FOUND("존재하지 않는 후기입니다.", HttpStatus.NOT_FOUND),
    REVIEW_ALREADY_EXISTS("이미 작성된 후기입니다.", HttpStatus.CONFLICT),
    REVIEW_APPLICATION_NOT_ATTENDED("출석 완료된 신청만 후기를 작성할 수 있습니다.", HttpStatus.BAD_REQUEST),
    REVIEW_APPLICATION_FORBIDDEN("본인의 신청에만 후기를 작성할 수 있습니다.", HttpStatus.FORBIDDEN),

    // Room / Item
    ITEM_NOT_FOUND("존재하지 않는 아이템입니다.", HttpStatus.NOT_FOUND),
    MILEAGE_NOT_ENOUGH("마일리지가 부족합니다.", HttpStatus.BAD_REQUEST),
    MILEAGE_ALREADY_REWARDED("이미 지급된 마일리지입니다.", HttpStatus.CONFLICT),
    MILEAGE_ADJUST_AMOUNT_ZERO("조정 금액은 0이 될 수 없습니다.", HttpStatus.BAD_REQUEST),

    // Carousel
    SLIDE_NOT_FOUND("존재하지 않는 슬라이드입니다.", HttpStatus.NOT_FOUND),
    GATHERING_ID_REQUIRED("GATHERING 타입은 gatheringId가 필수입니다.", HttpStatus.BAD_REQUEST),
    SLIDE_CONTENT_REQUIRED("STORY 타입은 content가 필수입니다.", HttpStatus.BAD_REQUEST),

    // Image
    INVALID_IMAGE_FORMAT("허용되지 않는 이미지 형식입니다.", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_FAILED("이미지 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;
}
