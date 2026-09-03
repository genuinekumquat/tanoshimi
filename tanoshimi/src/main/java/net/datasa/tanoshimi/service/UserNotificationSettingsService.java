package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserNotificationSettingsEntity;
import net.datasa.tanoshimi.repository.UserNotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [account-settings 신규] 알림 설정 - UserProfileThemeService 와 같은 upsert 패턴
 * (findByUser 로 있으면 갱신, 없으면 새로 만든다).
 *
 * <p>{@link #current}는 UserProfileThemeService.currentTheme() 의 "없으면 기본값" 아이디어를
 * 그대로 따르되, 필드가 많아 "기본값 DTO를 즉석에서 만들기"보다 "처음 조회할 때 기본값 행을
 * 만들어 저장해버리는" 쪽을 택했다(lazy create-on-read) - 그래야 화면에서 엔티티 하나만
 * 넘기면 되고, update() 도 항상 기존 행이 있다고 가정하고 단순하게 짤 수 있다.
 */
@Service
@RequiredArgsConstructor
public class UserNotificationSettingsService {

    private final UserNotificationSettingsRepository userNotificationSettingsRepository;

    @Transactional
    public UserNotificationSettingsEntity current(UserEntity user) {
        return userNotificationSettingsRepository.findByUser(user)
                .orElseGet(() -> userNotificationSettingsRepository.save(new UserNotificationSettingsEntity(user)));
    }

    @Transactional
    public void update(UserEntity user, boolean pushEnabled, boolean emailEnabled, boolean focusModeEnabled,
                        boolean notifyNewFollower, boolean notifyNewComment, boolean notifyPartyApplication,
                        boolean notifyPartyApproved, boolean notifyPartyRejected, boolean notifyPartyKicked,
                        boolean notifyTripReminder) {
        userNotificationSettingsRepository.findByUser(user).ifPresentOrElse(
                existing -> existing.update(pushEnabled, emailEnabled, focusModeEnabled,
                        notifyNewFollower, notifyNewComment, notifyPartyApplication,
                        notifyPartyApproved, notifyPartyRejected, notifyPartyKicked, notifyTripReminder),
                () -> {
                    UserNotificationSettingsEntity fresh = new UserNotificationSettingsEntity(user);
                    fresh.update(pushEnabled, emailEnabled, focusModeEnabled,
                            notifyNewFollower, notifyNewComment, notifyPartyApplication,
                            notifyPartyApproved, notifyPartyRejected, notifyPartyKicked, notifyTripReminder);
                    userNotificationSettingsRepository.save(fresh);
                });
    }
}
