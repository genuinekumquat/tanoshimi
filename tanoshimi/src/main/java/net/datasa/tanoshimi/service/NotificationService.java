package net.datasa.tanoshimi.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.NotificationView;
import net.datasa.tanoshimi.domain.entity.NotificationEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 인앱 알림함(종 아이콘). 여행 하루 전 알림은 TripReminderScheduler 가 이 서비스를 통해 발행한다. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void notify(UserEntity user, String type, String title, String message, String linkUrl) {
        NotificationEntity notif = notificationRepository.save(new NotificationEntity(user, type, title, message, linkUrl));
        try {
            NotificationView view = new NotificationView(
                    notif.getId(), notif.getType(), notif.getTitle(), notif.getMessage(),
                    notif.getLinkUrl(), notif.isRead(), notif.getCreatedAt() != null ? notif.getCreatedAt().format(TIME_FMT) : ""
            );
            messagingTemplate.convertAndSend("/topic/user." + user.getId() + ".notifications", view);
        } catch(Exception e) {
            // Ignore messaging errors
        }
    }

    /**
     * 엔티티를 그대로 컨트롤러로 내보내지 않고 DTO로 변환한다 - user 필드가 LAZY 라서
     * (open-in-view: false) 트랜잭션 밖에서 직렬화하면 LazyInitializationException 이 난다.
     */
    @Transactional(readOnly = true)
    public List<NotificationView> listFor(UserEntity user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(n -> new NotificationView(
                        n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                        n.getLinkUrl(), n.isRead(), n.getCreatedAt().format(TIME_FMT)))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UserEntity user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    /**
     * id 로 직접 받아서 이 메서드 안에서 조회한다(컨트롤러에서 미리 조회해 넘기지 않는다) -
     * 그래야 이 트랜잭션의 영속성 컨텍스트가 엔티티를 제대로 관리해서, markRead() 로 바뀐 값이
     * 실제로 DB 에 반영된다. 밖에서 조회해 넘긴 detached 엔티티를 여기서 필드만 바꾸면
     * Hibernate 가 변경을 감지 못 해 저장이 안 되는 문제가 있었다(PostService.toggleLike 와 동일한 버그).
     */
    @Transactional
    public void markRead(Long notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. id=" + notificationId));
        notification.markRead();
    }
}
