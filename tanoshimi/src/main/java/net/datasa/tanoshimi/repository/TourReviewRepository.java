package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TourEntity;
import net.datasa.tanoshimi.domain.entity.TourReviewEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourReviewRepository extends JpaRepository<TourReviewEntity, Long> {
    @EntityGraph(attributePaths = {"user"})
    List<TourReviewEntity> findByTourOrderByCreatedAtDesc(TourEntity tour);
}
