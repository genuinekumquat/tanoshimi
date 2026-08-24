package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PostEntity;
import net.datasa.tanoshimi.domain.entity.PostLikeEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLikeEntity, Long> {
    boolean existsByPostAndUser(PostEntity post, UserEntity user);
    Optional<PostLikeEntity> findByPostAndUser(PostEntity post, UserEntity user);
    void deleteByPostAndUser(PostEntity post, UserEntity user);

    /** 게시글 삭제 전에 이 글에 달린 좋아요를 먼저 지우기 위해 필요 - 안 지우면 FK 제약에 걸려 삭제가 실패한다. */
    void deleteByPost(PostEntity post);
}
