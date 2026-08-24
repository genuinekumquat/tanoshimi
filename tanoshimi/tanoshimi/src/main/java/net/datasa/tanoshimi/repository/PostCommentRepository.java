package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PostCommentEntity;
import net.datasa.tanoshimi.domain.entity.PostEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {
    /** board/detail.html 에서 c.user.name 을 바로 쓰므로 user 를 미리 JOIN FETCH 한다. */
    @Query("select c from PostCommentEntity c join fetch c.user where c.post = :post order by c.createdAt asc")
    List<PostCommentEntity> findByPostOrderByCreatedAtAsc(@Param("post") PostEntity post);

    /** 게시글 삭제 전에 이 글에 달린 댓글을 먼저 지우기 위해 필요 - 안 지우면 FK 제약에 걸려 삭제가 실패한다. */
    void deleteByPost(PostEntity post);
}
