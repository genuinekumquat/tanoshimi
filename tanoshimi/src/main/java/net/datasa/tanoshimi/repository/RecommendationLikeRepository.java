package net.datasa.tanoshimi.repository;

import java.util.Set;
import net.datasa.tanoshimi.domain.entity.Recommendation;
import net.datasa.tanoshimi.domain.entity.RecommendationLikeEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationLikeRepository extends JpaRepository<RecommendationLikeEntity, Long> {
    boolean existsByRecommendationAndUser(Recommendation recommendation, UserEntity user);
    void deleteByRecommendationAndUser(Recommendation recommendation, UserEntity user);

    /** 목록 화면에서 "내가 누른 하트"를 채워 보여주기 위한 추천글 id 목록. */
    @Query("select l.recommendation.id from RecommendationLikeEntity l where l.user.id = :userId")
    Set<Long> findRecommendationIdsByUserId(@Param("userId") Long userId);
}
