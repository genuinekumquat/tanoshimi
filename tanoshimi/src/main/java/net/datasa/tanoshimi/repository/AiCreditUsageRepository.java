package net.datasa.tanoshimi.repository;

import java.time.LocalDate;
import java.util.Optional;
import net.datasa.tanoshimi.domain.entity.AiCreditUsageEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCreditUsageRepository extends JpaRepository<AiCreditUsageEntity, Long> {
    Optional<AiCreditUsageEntity> findByUserAndUsageDate(UserEntity user, LocalDate usageDate);
}
