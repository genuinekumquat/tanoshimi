package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findBySocialProviderAndSocialId(String socialProvider, String socialId);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    org.springframework.data.domain.Page<UserEntity> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, org.springframework.data.domain.Pageable pageable);
}