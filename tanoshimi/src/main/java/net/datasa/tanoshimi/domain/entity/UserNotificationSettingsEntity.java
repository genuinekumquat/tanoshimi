package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [account-settings 신규] 마이페이지 > 계정 관리 > 알림 설정. 회원당 1행 -
 * UserProfileThemeEntity 와 같은 패턴(OneToOne + unique FK, findByUser 조회, 없으면 기본값).
 *
 * <p>여기 있는 push/email/focusMode 플래그는 현재 저장만 한다 - NotificationService(알림함)에는
 * 실제 푸시/이메일 발송 인프라가 없어서(Javadoc 참고) 이 값들이 실제 발송을 막거나 켜지는
 * 효과는 아직 없다. 발송 인프라가 생기면 그때 이 값들을 참조하도록 연결한다.
 *
 * <p>이벤트 타입별 토글은 NotificationService.notify(...) 호출부(FollowService,
 * PartyApplicationService, PartyService, PostService, TripReminderScheduler)에서 실제 쓰이는
 * type 문자열을 grep 해서 확정한 7종이다: new_follower, new_comment, party_application,
 * party_approved, party_rejected, party_kicked, trip_reminder.
 */
@Entity
@Getter
@Table(name = "user_notification_settings")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSettingsEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    /** 방해 금지(야간/집중) 모드. */
    @Column(name = "focus_mode_enabled", nullable = false)
    private boolean focusModeEnabled = false;

    @Column(name = "notify_new_follower", nullable = false)
    private boolean notifyNewFollower = true;

    @Column(name = "notify_new_comment", nullable = false)
    private boolean notifyNewComment = true;

    @Column(name = "notify_party_application", nullable = false)
    private boolean notifyPartyApplication = true;

    @Column(name = "notify_party_approved", nullable = false)
    private boolean notifyPartyApproved = true;

    @Column(name = "notify_party_rejected", nullable = false)
    private boolean notifyPartyRejected = true;

    @Column(name = "notify_party_kicked", nullable = false)
    private boolean notifyPartyKicked = true;

    @Column(name = "notify_trip_reminder", nullable = false)
    private boolean notifyTripReminder = true;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserNotificationSettingsEntity(UserEntity user) {
        this.user = user;
    }

    public void update(boolean pushEnabled, boolean emailEnabled, boolean focusModeEnabled,
                        boolean notifyNewFollower, boolean notifyNewComment, boolean notifyPartyApplication,
                        boolean notifyPartyApproved, boolean notifyPartyRejected, boolean notifyPartyKicked,
                        boolean notifyTripReminder) {
        this.pushEnabled = pushEnabled;
        this.emailEnabled = emailEnabled;
        this.focusModeEnabled = focusModeEnabled;
        this.notifyNewFollower = notifyNewFollower;
        this.notifyNewComment = notifyNewComment;
        this.notifyPartyApplication = notifyPartyApplication;
        this.notifyPartyApproved = notifyPartyApproved;
        this.notifyPartyRejected = notifyPartyRejected;
        this.notifyPartyKicked = notifyPartyKicked;
        this.notifyTripReminder = notifyTripReminder;
    }
}
