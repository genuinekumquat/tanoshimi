package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.NotificationEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    long countByUserAndReadFalse(UserEntity user);
}
