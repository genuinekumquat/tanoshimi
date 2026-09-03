package net.datasa.tanoshimi.repository;

import java.util.Optional;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserNotificationSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettingsEntity, Long> {
    Optional<UserNotificationSettingsEntity> findByUser(UserEntity user);
}
