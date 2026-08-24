package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.FollowEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.FollowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final NotificationService notificationService;

    @Transactional
    public void follow(UserEntity me, UserEntity target) {
        if (me.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 팔로우할 수 없습니다.");
        }
        if (!followRepository.existsByFollowerAndFollowee(me, target)) {
            followRepository.save(new FollowEntity(me, target));
            notificationService.notify(target, "new_follower",
                    "새 팔로워가 생겼어요",
                    me.getName() + "님이 회원님을 팔로우하기 시작했습니다.",
                    "/users/" + me.getId());
        }
    }

    @Transactional
    public void unfollow(UserEntity me, UserEntity target) {
        followRepository.deleteByFollowerAndFollowee(me, target);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UserEntity me, UserEntity target) {
        return followRepository.existsByFollowerAndFollowee(me, target);
    }

    @Transactional(readOnly = true)
    public long followerCount(UserEntity user) { return followRepository.countByFollowee(user); }

    @Transactional(readOnly = true)
    public long followingCount(UserEntity user) { return followRepository.countByFollower(user); }

    @Transactional(readOnly = true)
    public List<UserEntity> following(UserEntity user) {
        return followRepository.findByFollower(user).stream().map(FollowEntity::getFollowee).toList();
    }

    @Transactional(readOnly = true)
    public List<UserEntity> followers(UserEntity user) {
        return followRepository.findByFollowee(user).stream().map(FollowEntity::getFollower).toList();
    }
}
