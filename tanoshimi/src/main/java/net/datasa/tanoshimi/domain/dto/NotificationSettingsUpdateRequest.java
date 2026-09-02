package net.datasa.tanoshimi.domain.dto;

/**
 * [account-settings 신규] 알림 설정 저장 요청. notify_* 필드는
 * NotificationService.notify(...) 호출부에서 실제 쓰이는 7개 type 문자열과 1:1 대응한다.
 */
public record NotificationSettingsUpdateRequest(
        boolean pushEnabled,
        boolean emailEnabled,
        boolean focusModeEnabled,
        boolean notifyNewFollower,
        boolean notifyNewComment,
        boolean notifyPartyApplication,
        boolean notifyPartyApproved,
        boolean notifyPartyRejected,
        boolean notifyPartyKicked,
        boolean notifyTripReminder
) {}
