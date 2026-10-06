package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.NotificationEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    long countByUserAndReadFalse(UserEntity user);
    
    /** [DM 알림] 같은 방의 안 읽은 DM 알림이 이미 있는지 - 메시지마다 알림이 쌓이지 않게 한다. */
    boolean existsByUserAndTypeAndLinkUrlAndReadFalse(UserEntity user, String type, String linkUrl);
}
