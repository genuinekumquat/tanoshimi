package net.datasa.tanoshimi.scheduler;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyMemberEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.service.MannerTempService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [v16 신규] 파티 완료 자동처리 - 여행 종료일(departureDate + durationDays) 경과 시
 * 스케줄러가 자동으로 status 를 completed 로 전환한다. 이 시점에 참여자 전원 +0.5,
 * 방장 추가 +0.3 을 MannerTempService 에 위임해 기록하고, 완료된 파티는 이후
 * 공개 프로필에서 그 파티의 계획표를 열람할 수 있게 된다(PartyEntity.status=completed 로 판별).
 *
 * <p>담당: 허수연(⑤) - 관리자 화면의 "파티 완료 처리 현황"에서 이 스케줄러의 처리 결과를 모니터링한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyCompletionScheduler {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final MannerTempService mannerTempService;

    /** 매일 새벽 0시 30분 - 자정을 살짝 지나 그날 종료된 파티들을 정리한다. */
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void completeEndedParties() {
        List<PartyEntity> ended = partyRepository.findEndedButNotCompleted();
        int completed = 0;
        for (PartyEntity party : ended) {
            List<UserEntity> members = partyMemberRepository.findByParty(party).stream()
                    .map(PartyMemberEntity::getUser)
                    .collect(Collectors.toList());
            party.markCompleted();
            partyRepository.save(party);
            mannerTempService.awardPartyCompletion(party, members);
            completed++;
        }
        if (completed > 0) {
            log.info("파티 완료 자동처리: {}건 (참여자 전원 +0.5, 방장 추가 +0.3 매너온도 반영)", completed);
        }
    }
}
