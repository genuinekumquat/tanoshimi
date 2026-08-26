package net.datasa.tanoshimi.service;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.MannerTempLogEntity;
import net.datasa.tanoshimi.domain.entity.MannerTempReason;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.MannerTempLogRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매너온도 시스템 - v16 DB설계/기능명세서 확정 사항: "정책 정의·계산·트리거 구현 전체는
 * 이 서비스가 단독 소유한다. 다른 도메인은 이벤트 발생 시 호출만 한다." 어떤 다른 서비스도
 * user.applyMannerDelta() 를 직접 호출하지 말고 반드시 이 서비스를 거쳐야 한다 - 그래야
 * "왜 바뀌었는지"가 manner_temp_logs 에 항상 감사 로그로 남는다.
 *
 * <p>규칙(필드제약조건 확정 사항):
 * <ul>
 *   <li>범위: 0~50 캡 (UserEntity.applyMannerDelta 가 보장)</li>
 *   <li>파티 완료: 참여자 전원 +0.5, 그중 방장은 추가로 +0.3 (합 +0.8)</li>
 *   <li>신고 3회 누적 도달: -1.0 (누적 3의 배수를 새로 달성할 때마다 반복 적용)</li>
 *   <li>중도이탈·강퇴: -0.5</li>
 * </ul>
 * 회원가입 시 초기값(36.5)은 UserEntity 생성자에 이미 내장되어 있어 이 서비스가 관여하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MannerTempService {

    private static final BigDecimal PARTY_COMPLETE_DELTA = new BigDecimal("0.5");
    private static final BigDecimal HOST_BONUS_DELTA = new BigDecimal("0.3");
    private static final BigDecimal REPORT_PENALTY_DELTA = new BigDecimal("-1.0");
    private static final BigDecimal LEAVE_PENALTY_DELTA = new BigDecimal("-0.5");
    private static final int REPORT_PENALTY_THRESHOLD = 3;

    private final UserRepository userRepository;
    private final MannerTempLogRepository mannerTempLogRepository;

    /**
     * 파티 완료 자동처리 스케줄러 전용 - 참여자 전원 +0.5, 방장은 추가로 +0.3 을 더 받는다.
     * PartyCompletionScheduler 가 파티 하나를 completed 로 전환할 때마다 호출한다.
     */
    @Transactional
    public void awardPartyCompletion(PartyEntity party, List<UserEntity> members) {
        for (UserEntity member : members) {
            apply(member, PARTY_COMPLETE_DELTA, MannerTempReason.party_complete, party.getId());
            if (member.getId().equals(party.getOwner().getId())) {
                apply(member, HOST_BONUS_DELTA, MannerTempReason.host_bonus, party.getId());
            }
        }
    }

    /**
     * 신고 처리 전용 - 신고가 처리(resolved)될 때마다 호출한다. 이 사용자를 대상으로 한
     * 누적 처리 신고 건수가 3의 배수에 "새로" 도달했을 때만 -1.0 을 적용한다(반복 적용 가능).
     *
     * @param cumulativeResolvedCount 이 신고를 포함해서 지금까지 이 사용자를 대상으로
     *                                처리 완료된 신고의 총 건수 (호출자인 ReportService 가 계산해서 넘긴다)
     */
    @Transactional
    public void applyReportPenaltyIfThresholdReached(UserEntity offendingUser, long cumulativeResolvedCount, Long reportId) {
        if (cumulativeResolvedCount > 0 && cumulativeResolvedCount % REPORT_PENALTY_THRESHOLD == 0) {
            apply(offendingUser, REPORT_PENALTY_DELTA, MannerTempReason.report_penalty, reportId);
        }
    }

    /** 파티 중도이탈(본인) 또는 강퇴 시 호출 - -0.5. */
    @Transactional
    public void applyLeaveOrKickPenalty(UserEntity user, Long partyId) {
        apply(user, LEAVE_PENALTY_DELTA, MannerTempReason.leave_penalty, partyId);
    }

    /**
     * 실제 증감 + 로그 기록. UserEntity 는 호출자가 이미 조회해 넘기는 경우가 많으므로,
     * detached 상태로 변경분이 유실되지 않도록 여기서 명시적으로 save() 한다
     * (PostService.toggleLike 등에서 겪었던 것과 동일한 패턴 - 항상 명시적으로 저장한다).
     */
    private void apply(UserEntity user, BigDecimal delta, MannerTempReason reason, Long relatedId) {
        user.applyMannerDelta(delta);
        userRepository.save(user);
        mannerTempLogRepository.save(new MannerTempLogEntity(user, delta, reason, relatedId));
    }
}
