package com.whatsuphouse.backend.domain.payment.enums;

/** 입금 알림 출처. 웹훅은 원문만 받으므로 출처를 가리지 않고 같은 대사 경로를 탄다. (KAN-403) */
public enum DepositSource {
    MACRODROID,     // 안드로이드 은행 앱 푸시
    IOS_SHORTCUT,   // 아이폰 단축어(은행 문자)
    SIMULATOR       // 심사용 입금 시뮬레이터
}
