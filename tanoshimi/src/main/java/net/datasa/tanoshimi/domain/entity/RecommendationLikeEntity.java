package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 관광지 추천 좋아요 - 한 사람이 같은 추천글에 한 번만 누를 수 있게 (recommendation_id, user_id) 유니크. */
@Entity
@Getter
@Table(name = "recommendation_likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_rl_rec_user", columnNames = {"recommendation_id", "user_id"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationLikeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public RecommendationLikeEntity(Recommendation recommendation, UserEntity user) {
        this.recommendation = recommendation; this.user = user;
    }
}
