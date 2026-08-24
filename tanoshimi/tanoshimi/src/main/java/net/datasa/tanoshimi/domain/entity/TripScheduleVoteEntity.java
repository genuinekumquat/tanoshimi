package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 완성된 계획표에 대한 파티원 찬반 투표. */
@Entity
@Getter
@Table(name = "trip_schedule_votes")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripScheduleVoteEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_id", nullable = false)
    private TripScheduleEntity schedule;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteType vote;

    @CreatedDate
    @Column(name = "voted_at", nullable = false, updatable = false)
    private LocalDateTime votedAt;

    public TripScheduleVoteEntity(TripScheduleEntity schedule, UserEntity user, VoteType vote) {
        this.schedule = schedule; this.user = user; this.vote = vote;
    }

    public void changeVote(VoteType vote) { this.vote = vote; }
}
