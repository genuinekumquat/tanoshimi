package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.MannerTempLogEntity;
import net.datasa.tanoshimi.domain.entity.MannerTempReason;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MannerTempLogRepository extends JpaRepository<MannerTempLogEntity, Long> {
    List<MannerTempLogEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    long countByUserAndReason(UserEntity user, MannerTempReason reason);
}
