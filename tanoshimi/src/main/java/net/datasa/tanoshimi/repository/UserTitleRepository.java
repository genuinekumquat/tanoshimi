package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TitleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserTitleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTitleRepository extends JpaRepository<UserTitleEntity, Long> {
    boolean existsByUserAndTitle(UserEntity user, TitleEntity title);

    Optional<UserTitleEntity> findByUserAndTitle(UserEntity user, TitleEntity title);

    /**
     * TitleService.latestTitle() 에서 uti.getTitle() 을 바로 꺼내 쓰고, 그게 마이페이지
     * 템플릿에서 myTitle.name 처럼 곧장 필드 접근되기 때문에, title(LAZY 연관)을 미리
     * JOIN FETCH 해서 가져와야 한다. 안 그러면 렌더링 시점에 LazyInitializationException 이 난다.
     */
    @EntityGraph(attributePaths = {"title"})
    List<UserTitleEntity> findByUserOrderByEarnedAtDesc(UserEntity user);

    /** [TNSM-20] 현재 장착한 대표 칭호. 위와 같은 이유로 title 을 미리 JOIN FETCH 한다. */
    @EntityGraph(attributePaths = {"title"})
    Optional<UserTitleEntity> findByUserAndEquippedTrue(UserEntity user);
}
