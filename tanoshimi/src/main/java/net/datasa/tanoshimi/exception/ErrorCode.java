package net.datasa.tanoshimi.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 회원/인증
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호입니다."),
    UNDERAGE(HttpStatus.BAD_REQUEST, "만 14세 이상만 가입할 수 있습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),

    // 본인인증 (휴대폰/이메일 공용 - PhoneVerificationService, EmailVerificationService 둘 다 사용)
    VERIFICATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "인증 요청 내역이 없습니다."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호 유효시간이 지났습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    VERIFICATION_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다."),
    VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "본인인증을 먼저 완료해 주세요."),
    VERIFICATION_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 요청해 주세요."),
    VERIFICATION_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "하루 인증 요청 횟수를 초과했습니다."),
    EMAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    // 소셜
    SOCIAL_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "소셜 로그인 정보가 만료되었습니다."),

    // 비밀번호 재발급/변경
    SOCIAL_ACCOUNT_NO_PASSWORD(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호 재발급을 지원하지 않습니다. 소셜 로그인으로 이용해 주세요."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),

    // 투어/예약/결제
    TOUR_NOT_FOUND(HttpStatus.NOT_FOUND, "패키지를 찾을 수 없습니다."),
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "파티를 찾을 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "포인트가 부족합니다. 충전 후 다시 시도해 주세요."),
    WEATHER_ACK_REQUIRED(HttpStatus.BAD_REQUEST, "날씨 안내를 확인하고 진행 여부를 다시 선택해 주세요."),

    // 파티 자격
    PARTY_GENDER_RESTRICTED(HttpStatus.FORBIDDEN, "이 파티는 성별 조건이 맞지 않아 신청할 수 없습니다."),
    PARTY_AGE_RESTRICTED(HttpStatus.FORBIDDEN, "이 파티는 연령 조건이 맞지 않아 신청할 수 없습니다."),
    PARTY_NATIONALITY_RESTRICTED(HttpStatus.FORBIDDEN, "이 파티는 국적 조건이 맞지 않아 신청할 수 없습니다."),
    PARTY_FULL(HttpStatus.CONFLICT, "이미 정원이 가득 찼습니다."),
    PARTY_RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "모집이 마감된 파티입니다."),
    NOT_PARTY_MEMBER(HttpStatus.FORBIDDEN, "파티원만 볼 수 있는 페이지입니다."),

    // 계획표
    SCHEDULE_NOT_DRAFT(HttpStatus.CONFLICT, "이미 제출된 계획표는 자유롭게 수정할 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "계획표를 찾을 수 없습니다."),
    ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND, "액티비티를 찾을 수 없습니다."),

    // [v16 신규] 계획표 편집권(lock) / 스냅샷 롤백
    LOCK_NOT_HELD(HttpStatus.FORBIDDEN, "지금은 편집권을 가진 사람만 수정할 수 있어요."),
    LOCK_ALREADY_HELD(HttpStatus.CONFLICT, "이미 다른 파티원이 편집 중이에요."),
    SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "저장 시점을 찾을 수 없습니다."),

    // [v16 신규] AI 크레딧
    AI_CREDIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘의 AI 추천 크레딧을 모두 사용했어요. 내일 다시 시도해 주세요."),

    // [v16 신규] 유저 차단 (TNSM-96)
    BLOCKED_USER(HttpStatus.FORBIDDEN, "차단 관계인 상대에게는 메시지를 보낼 수 없습니다."),
    CANNOT_APPLY_BLOCKED_PARTY(HttpStatus.FORBIDDEN, "차단 관계인 파티장의 파티에는 신청할 수 없습니다."),

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public HttpStatus status() { return status; }
    public String message() { return message; }
}
