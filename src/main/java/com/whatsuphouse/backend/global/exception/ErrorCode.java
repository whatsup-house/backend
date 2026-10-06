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
    INVALID_PASSWORD_RESET_TOKEN("유효하지 않은 비밀번호 재설정 링크입니다.", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_VERIFICATION_CODE("인증번호가 올바르지 않거나 만료되었습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED("이메일 인증이 필요합니다.", HttpStatus.FORBIDDEN),
    GUEST_PARTICIPANT_NOT_FOUND("일치하는 비회원 이용내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // User
    DUPLICATE_NICKNAME("이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    INVALID_PAGE_SIZE("페이지 사이즈는 1 이상 100 이하여야 합니다.", HttpStatus.BAD_REQUEST),

    // Gathering
    GATHERING_NOT_FOUND("존재하지 않는 게더링입니다.", HttpStatus.NOT_FOUND),
    GATHERING_FULL("게더링 정원이 초과되었습니다.", HttpStatus.BAD_REQUEST),
    GATHERING_NOT_RECRUITING("모집중인 게더링이 아닙니다.", HttpStatus.BAD_REQUEST),
    INVALID_GATHERING_DATE("게더링 날짜는 오늘 이후로 선택해주세요.", HttpStatus.BAD_REQUEST),
    INVALID_GATHERING_TIME("시작 시간은 종료 시간보다 빨라야 합니다.", HttpStatus.BAD_REQUEST),
    SESSION_NOT_FOUND("존재하지 않는 회차입니다.", HttpStatus.NOT_FOUND),
    SESSION_GATHERING_MISMATCH("신청한 모임의 회차가 아닙니다.", HttpStatus.BAD_REQUEST),
    SINGLE_SESSION_REQUIRED("일반 모임은 회차를 하나만 선택해야 합니다.", HttpStatus.BAD_REQUEST),
    APPLY_DEADLINE_PASSED("신청 마감 시간이 지난 회차입니다.", HttpStatus.BAD_REQUEST),
    SESSION_HAS_APPLICATIONS("신청이 있는 회차는 삭제할 수 없습니다.", HttpStatus.CONFLICT),
    INVALID_REPEAT_RANGE("반복 종료일은 회차 날짜부터 1년 이내로 선택해주세요.", HttpStatus.BAD_REQUEST),

    // Form
    FORM_NOT_FOUND("신청폼이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    QUESTION_NOT_FOUND("존재하지 않는 질문입니다.", HttpStatus.NOT_FOUND),
    RESERVED_QUESTION_READONLY("시스템 예약 질문(이름/연락처)은 삭제하거나 질문 키를 변경할 수 없습니다.", HttpStatus.BAD_REQUEST),
    RESERVED_QUESTION_LOCKED("우연한 식탁 표준 질문은 삭제하거나 질문 키·타입을 변경할 수 없습니다.", HttpStatus.BAD_REQUEST),
    STANDARD_QUESTIONS_MISSING("우연한 식탁 폼에는 표준 질문 7개가 모두 있어야 합니다.", HttpStatus.BAD_REQUEST),
    OPTIONS_REQUIRED("선택형 질문은 options가 필수입니다.", HttpStatus.BAD_REQUEST),
    MATCHING_STRATEGY_REQUIRED("매칭 필드는 matchingStrategy가 필수입니다.", HttpStatus.BAD_REQUEST),
    MATCHING_FIELD_TYPE_NOT_ALLOWED("주관식 질문은 매칭에 사용할 수 없습니다.", HttpStatus.BAD_REQUEST),
    REQUIRED_ANSWER_MISSING("필수 질문에 답변하지 않았습니다.", HttpStatus.BAD_REQUEST),
    INVALID_QUESTION("해당 폼에 속하지 않는 질문입니다.", HttpStatus.BAD_REQUEST),
    INVALID_MBTI("유효하지 않은 MBTI 값입니다.", HttpStatus.BAD_REQUEST),
    INVALID_JOB("유효하지 않은 직업 코드입니다.", HttpStatus.BAD_REQUEST),
    INVALID_BIRTH_YEAR("출생연도는 1900년부터 올해까지의 연도(예: 1995)로 입력해 주세요.", HttpStatus.BAD_REQUEST),

    // Matching
    MATCHING_NOT_ALLOWED("우연한 식탁(RANDOM_TABLE) 게더링만 자동매칭할 수 있습니다.", HttpStatus.BAD_REQUEST),
    MATCHING_GROUP_NOT_FOUND("존재하지 않는 매칭 그룹입니다.", HttpStatus.NOT_FOUND),
    MATCHING_MEMBER_NOT_FOUND("존재하지 않는 매칭 멤버입니다.", HttpStatus.NOT_FOUND),
    MATCHING_ALREADY_ASSIGNED("이미 다른 그룹에 배정된 신청입니다.", HttpStatus.BAD_REQUEST),
    SESSION_NOT_MATCHABLE("취소되었거나 종료된 회차는 매칭할 수 없습니다.", HttpStatus.CONFLICT),
    // 테이블 수동 조정·재조정 (KAN-347)
    TABLE_NOT_ADJUSTABLE("제안·확정 상태의 테이블만 조정할 수 있습니다.", HttpStatus.BAD_REQUEST),
    TABLE_SESSION_MISMATCH("같은 회차의 서로 다른 테이블끼리만 조정할 수 있습니다.", HttpStatus.BAD_REQUEST),
    TABLE_ADJUST_REASON_REQUIRED("확정된 테이블을 조정하려면 사유가 필요합니다.", HttpStatus.BAD_REQUEST),
    TABLE_RULE_VIOLATION("조정 결과가 테이블 규칙(인원·나이 차·제외 관계)을 어깁니다.", HttpStatus.BAD_REQUEST),
    APPLICATION_NOT_ASSIGNABLE("이 회차의 결제 완료 신청만 배정할 수 있습니다.", HttpStatus.BAD_REQUEST),
    // 매칭 해결 선택 (KAN-347)
    RESOLUTION_NOT_FOUND("존재하지 않는 해결 선택입니다.", HttpStatus.NOT_FOUND),
    RESOLUTION_ALREADY_HANDLED("이미 처리된 해결 선택입니다.", HttpStatus.CONFLICT),
    RESOLUTION_SESSION_REQUIRED("회차 이동은 옮길 회차를 골라야 합니다.", HttpStatus.BAD_REQUEST),
    RESOLUTION_SESSION_NOT_OFFERED("제안된 회차 중 아직 모집 중인 회차만 고를 수 있습니다.", HttpStatus.BAD_REQUEST),

    // Dining (우연한 식탁 운영자 어드민)
    NOT_RANDOM_TABLE_SESSION("우연한 식탁 회차가 아닙니다.", HttpStatus.BAD_REQUEST),
    EXCEPTION_CASE_NOT_FOUND("존재하지 않는 예외 건입니다.", HttpStatus.NOT_FOUND),
    EXCEPTION_NOTE_REQUIRED("예외를 처리하려면 처리 메모가 필요합니다.", HttpStatus.BAD_REQUEST),
    EXCEPTION_ACTION_NOT_ALLOWED("조치는 안전(SAFETY) 예외를 처리할 때만 지정할 수 있습니다.", HttpStatus.BAD_REQUEST),
    EXCEPTION_ACTION_TARGET_MISSING("조치할 회원 신청이 연결되지 않은 예외입니다.", HttpStatus.BAD_REQUEST),
    INVALID_TABLE_SIZE_RANGE("테이블 최소 인원은 최대 인원보다 클 수 없습니다.", HttpStatus.BAD_REQUEST),
    VENUE_NOT_FOUND("존재하지 않는 식당입니다.", HttpStatus.NOT_FOUND),
    VENUE_INACTIVE("비활성 식당은 배정할 수 없습니다.", HttpStatus.BAD_REQUEST),
    VENUE_NOT_IN_SESSION("회차 식당 풀에 없는 식당입니다.", HttpStatus.BAD_REQUEST),
    DUPLICATE_SESSION_VENUE("같은 식당을 중복으로 설정할 수 없습니다.", HttpStatus.BAD_REQUEST),
    VENUE_CAPACITY_EXCEEDED("식당의 수용 테이블 수를 넘을 수 없습니다.", HttpStatus.CONFLICT),
    TABLE_NOT_PROPOSED("제안(PROPOSED) 상태 테이블만 확정할 수 있습니다.", HttpStatus.CONFLICT),
    TABLE_NOT_CONFIRMED("확정된 테이블에서만 할 수 있습니다.", HttpStatus.CONFLICT),
    DINING_TABLE_FORBIDDEN("본인이 속한 확정 테이블만 볼 수 있습니다.", HttpStatus.FORBIDDEN),
    SYSTEM_ADMIN_NOT_FOUND("채팅방을 만들 시스템 관리자 계정이 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    // 참석·체크인 (KAN-349)
    CHECKIN_WINDOW_CLOSED("체크인은 회차 시작 2시간 전부터 2시간 후까지만 할 수 있습니다.", HttpStatus.BAD_REQUEST),
    ATTENDANCE_NOT_FOUND("존재하지 않는 참석 기록입니다.", HttpStatus.NOT_FOUND),

    // Dining (참가자 피드백·신고, KAN-350)
    NOT_TABLE_MEMBER("이 테이블의 멤버가 아닙니다.", HttpStatus.FORBIDDEN),
    FEEDBACK_NOT_OPEN("아직 피드백을 남길 수 없는 테이블입니다.", HttpStatus.BAD_REQUEST),
    FEEDBACK_ALREADY_SUBMITTED("이미 피드백을 남긴 테이블입니다.", HttpStatus.CONFLICT),
    INVALID_TABLE_PEER("같은 테이블의 다른 멤버만 한 번씩 지정할 수 있습니다.", HttpStatus.BAD_REQUEST),
    SELF_REPORT_NOT_ALLOWED("자기 자신은 신고할 수 없습니다.", HttpStatus.BAD_REQUEST),

    // Application
    APPLICATION_NOT_FOUND("존재하지 않는 신청입니다.", HttpStatus.NOT_FOUND),
    ALREADY_APPLIED("이미 신청한 게더링입니다.", HttpStatus.CONFLICT),
    CANNOT_CANCEL("취소할 수 없는 신청입니다.", HttpStatus.BAD_REQUEST),
    CANCEL_WINDOW_CLOSED("행사 시작 2일 전까지만 취소할 수 있습니다.", HttpStatus.BAD_REQUEST),
    APPLICATION_ALREADY_CANCELLED("이미 취소된 신청입니다.", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION("허용되지 않는 상태 변경입니다.", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE("출석 완료된 신청은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST),
    APPLICATION_FORBIDDEN("본인의 신청이 아닙니다.", HttpStatus.FORBIDDEN),
    GUEST_PHONE_REQUIRED("비회원 신청 시 전화번호는 필수입니다.", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT("올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
    ALREADY_ATTENDED("이미 출석 처리된 신청입니다.", HttpStatus.CONFLICT),
    PARTICIPANT_BLOCKED("차단된 참가자는 우연한 식탁을 신청할 수 없습니다.", HttpStatus.FORBIDDEN),
    RANDOM_TABLE_ELIGIBILITY_RESTRICTED("우연한 식탁 참여 자격이 제한된 상태입니다.", HttpStatus.FORBIDDEN),
    RANDOM_TABLE_MEMBERS_ONLY("우연한 식탁은 회원만 신청할 수 있습니다.", HttpStatus.FORBIDDEN),
    REJECTION_REASON_REQUIRED("신청 거절 사유는 필수입니다.", HttpStatus.BAD_REQUEST),

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

    // Feed (KAN-380)
    FEED_POST_NOT_FOUND("존재하지 않는 피드 게시물입니다.", HttpStatus.NOT_FOUND),
    INVALID_FEED_VIDEO_URL("허용되지 않는 영상 URL입니다.", HttpStatus.BAD_REQUEST),
    INVALID_FEED_CURSOR("유효하지 않은 피드 커서입니다.", HttpStatus.BAD_REQUEST),

    // Image
    INVALID_IMAGE_FORMAT("허용되지 않는 이미지 형식입니다.", HttpStatus.BAD_REQUEST),
    INVALID_UPLOAD_FOLDER("허용되지 않는 업로드 폴더입니다.", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_FAILED("이미지 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // Ticket Pass
    TICKET_PASS_NOT_FOUND("존재하지 않는 이용권입니다.", HttpStatus.NOT_FOUND),
    TICKET_ALREADY_PROCESSED("이미 처리된 이용권입니다.", HttpStatus.BAD_REQUEST),
    NO_AVAILABLE_TICKET("사용 가능한 이용권이 없습니다. 이용권을 먼저 구매해주세요.", HttpStatus.BAD_REQUEST),
    TICKET_PURCHASE_NOT_ALLOWED("우연한 식탁 참여 승인 후 이용권을 구매할 수 있습니다.", HttpStatus.FORBIDDEN),
    INVALID_TICKET_ADJUSTMENT("유효하지 않은 이용권 횟수 조정입니다.", HttpStatus.BAD_REQUEST),
    TICKET_PRODUCT_NOT_FOUND("존재하지 않는 이용권 상품입니다.", HttpStatus.NOT_FOUND),
    INVALID_REFUND_TRANSITION("환불을 진행할 수 없는 이용권 차감 기록입니다.", HttpStatus.CONFLICT),

    // Rate Limit
    TOO_MANY_REQUESTS("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),

    // Mail Template
    MAIL_TEMPLATE_NOT_FOUND("존재하지 않는 메일 템플릿입니다.", HttpStatus.NOT_FOUND),

    // Notification
    NOTIFICATION_NOT_FOUND("존재하지 않는 알림입니다.", HttpStatus.NOT_FOUND),

    // Chat
    CHAT_NOT_MEMBER("채팅방 멤버가 아닙니다.", HttpStatus.FORBIDDEN),
    CHAT_MUTED("채팅이 금지된 상태입니다.", HttpStatus.FORBIDDEN),
    CHAT_ROOM_TYPE_MISMATCH("이 채팅방 유형에서는 할 수 없는 작업입니다.", HttpStatus.BAD_REQUEST),
    CHAT_ROOM_NOT_FOUND("존재하지 않는 채팅방입니다.", HttpStatus.NOT_FOUND),
    CHAT_MESSAGE_NOT_FOUND("존재하지 않는 메시지입니다.", HttpStatus.NOT_FOUND),
    CHAT_MESSAGE_FORBIDDEN("메시지를 수정하거나 삭제할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CHAT_INVALID_MESSAGE("올바르지 않은 메시지입니다.", HttpStatus.BAD_REQUEST),
    CHAT_INVALID_EMOJI("사용할 수 없는 이모지입니다.", HttpStatus.BAD_REQUEST),
    CHAT_IMAGE_TOO_LARGE("이미지는 5MB 이하만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),
    CHAT_REPORT_NOT_FOUND("존재하지 않는 신고입니다.", HttpStatus.NOT_FOUND),
    CHAT_CRYPTO_FAILED("메시지를 처리하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CHAT_PUSH_DISABLED("웹 푸시를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    CHAT_PUSH_INVALID_ENDPOINT("올바르지 않은 푸시 구독 주소입니다.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus status;
}
