package net.datasa.tanoshimi.repository;

import java.util.Optional;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserProfileThemeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileThemeRepository extends JpaRepository<UserProfileThemeEntity, Long> {
    Optional<UserProfileThemeEntity> findByUser(UserEntity user);
}
