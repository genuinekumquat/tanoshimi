package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.SupportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportRepository extends JpaRepository<SupportEntity, Long> {
    List<SupportEntity> findAllByOrderByIdDesc();
}
