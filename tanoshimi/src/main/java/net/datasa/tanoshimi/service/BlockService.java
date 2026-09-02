package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.UserBlockEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TNSM-96 유저 차단.
 * 차단 여부는 상태를 별도로 캐싱하지 않고 매번 user_blocks 를 조회해서 판단한다
 * (docs/user_blocks_design_decisions.txt 참고 - chat_rooms 등에 플래그 컬럼을 두지 않기로 함).
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final FollowService followService;

    @Transactional
    public void block(UserEntity me, UserEntity target) {
        if (me.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 차단할 수 없습니다.");
        }
        if (!userBlockRepository.existsByBlockerAndBlocked(me, target)) {
            userBlockRepository.save(new UserBlockEntity(me, target));
        }
        // 차단 시 팔로우 관계 자동 해제 (양방향 전부)
        followService.unfollow(me, target);
        followService.unfollow(target, me);
    }

    @Transactional
    public void unblock(UserEntity me, UserEntity target) {
        userBlockRepository.deleteByBlockerAndBlocked(me, target);
    }

    /** 둘 중 누가 차단했든 상관없이 서로 차단 관계인지 (채팅/게시판/파티 신청 시점에 매번 조회). */
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UserEntity a, UserEntity b) {
        return userBlockRepository.existsBetween(a, b);
    }

    /** 로그인 사용자와 차단 관계(양방향)에 있는 상대방 id 목록 - 게시판 등 목록 필터링용. */
    @Transactional(readOnly = true)
    public List<Long> relatedBlockedUserIds(UserEntity user) {
        return userBlockRepository.findRelatedUserIds(user);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedByMe(UserEntity me, UserEntity target) {
        return userBlockRepository.existsByBlockerAndBlocked(me, target);
    }
}
