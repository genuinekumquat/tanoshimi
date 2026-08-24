package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TitleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<TitleEntity, Long> {
    Optional<TitleEntity> findByCode(String code);
}
