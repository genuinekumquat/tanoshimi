package net.datasa.tanoshimi.repository;

import java.util.List;
import net.datasa.tanoshimi.domain.entity.UserBlockEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlockEntity, Long> {

    boolean existsByBlockerAndBlocked(UserEntity blocker, UserEntity blocked);
    void deleteByBlockerAndBlocked(UserEntity blocker, UserEntity blocked);
    List<UserBlockEntity> findByBlocker(UserEntity blocker);

    /** 둘 중 누가 누구를 차단했든 상관없이, 두 사람 사이에 차단 관계가 있는지 조회 시점에 판단한다. */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM UserBlockEntity b " +
           "WHERE (b.blocker = :a AND b.blocked = :b) OR (b.blocker = :b AND b.blocked = :a)")
    boolean existsBetween(@Param("a") UserEntity a, @Param("b") UserEntity b);

    /** 로그인 사용자와 차단 관계(양방향)에 있는 상대방 id 전체 - 목록 조회 필터링용. */
    @Query("SELECT CASE WHEN b.blocker = :user THEN b.blocked.id ELSE b.blocker.id END " +
           "FROM UserBlockEntity b WHERE b.blocker = :user OR b.blocked = :user")
    List<Long> findRelatedUserIds(@Param("user") UserEntity user);
}
