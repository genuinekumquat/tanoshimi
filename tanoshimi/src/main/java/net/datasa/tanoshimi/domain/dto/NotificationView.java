package net.datasa.tanoshimi.domain.dto;

/**
 * 알림 목록 응답 전용 뷰.
 * NotificationEntity 를 그대로 JSON 으로 내려주면 안 된다 - user 필드가 LAZY 라
 * (application.yml 의 open-in-view: false 때문에) 트랜잭션이 끝난 뒤 컨트롤러가
 * 응답을 직렬화하는 시점엔 세션이 이미 닫혀 있어 LazyInitializationException 이 난다.
 * 그래서 서비스 계층에서 이 DTO로 미리 변환해서 내려준다.
 */
public record NotificationView(
        Long id, String type, String title, String message, String linkUrl, boolean read, String createdAt
) {
}
