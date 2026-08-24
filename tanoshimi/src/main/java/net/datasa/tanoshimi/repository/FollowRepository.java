package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.FollowEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<FollowEntity, Long> {
    boolean existsByFollowerAndFollowee(UserEntity follower, UserEntity followee);
    void deleteByFollowerAndFollowee(UserEntity follower, UserEntity followee);
    List<FollowEntity> findByFollower(UserEntity follower);   // 내가 팔로우하는 사람들
    List<FollowEntity> findByFollowee(UserEntity followee);   // 나를 팔로우하는 사람들
    long countByFollower(UserEntity follower);
    long countByFollowee(UserEntity followee);
}
