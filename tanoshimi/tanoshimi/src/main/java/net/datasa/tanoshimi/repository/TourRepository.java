package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.TourEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourRepository extends JpaRepository<TourEntity, Long> {
    List<TourEntity> findByStatusOrderByIdDesc(ActiveStatus status);
    List<TourEntity> findByRegionAndStatus(String region, ActiveStatus status);
    List<TourEntity> findByStatusOrderByPriceKrwAsc(ActiveStatus status);
    List<TourEntity> findByStatusOrderByPriceKrwDesc(ActiveStatus status);
    List<TourEntity> findByRegionAndStatusOrderByPriceKrwAsc(String region, ActiveStatus status);
    List<TourEntity> findByRegionAndStatusOrderByPriceKrwDesc(String region, ActiveStatus status);
}
