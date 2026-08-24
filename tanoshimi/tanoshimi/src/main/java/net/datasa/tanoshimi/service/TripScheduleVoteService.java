package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleVoteEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.VoteType;
import net.datasa.tanoshimi.repository.TripScheduleVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 완성된 계획표에 대한 파티원 찬반 투표. */
@Service
@RequiredArgsConstructor
public class TripScheduleVoteService {

    private final TripScheduleVoteRepository voteRepository;

    @Transactional
    public void vote(TripScheduleEntity schedule, UserEntity user, VoteType voteType) {
        voteRepository.findByScheduleAndUser(schedule, user)
                .ifPresentOrElse(
                        existing -> existing.changeVote(voteType),
                        () -> voteRepository.save(new TripScheduleVoteEntity(schedule, user, voteType))
                );
    }

    public record Tally(long agree, long disagree, long total) {
        public boolean isPassing() { return total > 0 && agree > disagree; }
    }

    @Transactional(readOnly = true)
    public Tally tally(TripScheduleEntity schedule) {
        var votes = voteRepository.findBySchedule(schedule);
        long agree = votes.stream().filter(v -> v.getVote() == VoteType.agree).count();
        long disagree = votes.stream().filter(v -> v.getVote() == VoteType.disagree).count();
        return new Tally(agree, disagree, votes.size());
    }
}
