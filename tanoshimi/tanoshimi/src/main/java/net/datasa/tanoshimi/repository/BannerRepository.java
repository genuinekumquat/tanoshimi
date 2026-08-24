package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.BannerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<BannerEntity, Long> {
    List<BannerEntity> findAllByOrderBySortOrderAsc();
}
