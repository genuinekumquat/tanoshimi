package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * "내 여행" - 마이페이지 여행 횟수/지역 집계의 단일 근거. 담당: 김민규(⑥ 마이페이지). v19 신규.
 *
 * <p><b>왜 생겼나</b> - v18까지는 완료된 파티(PartyStatus.completed)와 지역 태그 스냅
 * (posts.region, party 없는 것)을 TitleService/TravelHeatmapService가 직접 스캔해서
 * "여행 횟수"를 셌다. 두 가지 문제가 있었다.
 * <ol>
 *   <li>파티는 스냅을 한 장도 안 올려도 완료되기만 하면 카운트됐다 - 지도에 스냅이 하나도
 *       없는 권역인데도 "8도 정복자" 같은 칭호가 붙는 것처럼 보이는 원인이었다.</li>
 *   <li>개별 여행은 스냅 단위로 셌는데, 한 번 여행에서 스냅을 며칠에 걸쳐 여러 장 올리면
 *       실제로는 한 번 다녀온 여행인데 횟수가 여러 번으로 뻥튀기될 수 있었다.</li>
 * </ol>
 * v19부터는 "여행"을 사용자가(또는 파티 완료가) 명시적으로 등록한 이 테이블의 행 하나로
 * 정의한다. TitleService/TravelHeatmapService는 더 이상 parties/posts를 직접 스캔하지 않고
 * 이 엔티티 목록만 본다(MyTripService.listMine 참고) - 그래서 두 문제 모두 근본적으로
 * 해결된다: 파티 여행은 여전히 파티 완료라는 확실한 근거로 자동 등록되고(스냅 유무와 무관 -
 * 파티 완료 자체가 이미 검증된 여행 증거라 그대로 둔다), 개별 여행은 사용자가 "몇 번 갔는지"를
 * 스냅 개수가 아니라 직접 명시적으로 등록/삭제하게 된다.
 *
 * <p><b>source</b> - PARTY(파티 완료 시 자동 생성) / SOLO(사용자가 직접 등록). 파티 완료 자동
 * 등록은 PartyCompletionScheduler(⑤ 소유)를 건드리지 않고, 기존 칭호 부여와 같은 방식으로
 * 마이페이지를 열 때 MyTripService.syncFromCompletedParties 가 멱등하게(이미 있으면 건너뜀)
 * 만들어준다 - 스케줄러를 임의로 고치지 않기로 한 기존 판단(TitleService 클래스 주석)을 그대로
 * 따른다.
 *
 * <p><b>destination</b>은 위치태그 선택이 아니라 자유 입력이다(게시글 지역 입력과 같은 수준 -
 * 둘 다 오채원(④)의 드롭다운/태그검색 개선 대상, Phase 2). 8도 정복자 등 지역 판정은
 * TitleService.normalizeRegion으로 시/도 단위로 접어서 쓴다.
 *
 * <p>스냅(posts)은 이제 여행의 "근거"가 아니라 "기록물"이다 - 스냅을 올릴 때 등록된 여행 중
 * 하나를 선택하면(PostEntity.trip) 그 여행에 딸린 사진으로 묶이지만, 여행 횟수 집계에는
 * 영향을 주지 않는다(중복 집계 원천 차단).
 */
@Entity
@Getter
@Table(name = "my_trips")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyTripEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripSource source;

    /** source=PARTY일 때만 채워진다. 같은 파티가 두 번 자동 등록되지 않게 막는 대조 키이기도 하다. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_id")
    private PartyEntity party;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 500)
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public MyTripEntity(UserEntity user, TripSource source, PartyEntity party, String title,
                        String destination, LocalDate startDate, LocalDate endDate, String memo) {
        this.user = user;
        this.source = source;
        this.party = party;
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = (endDate == null || endDate.isBefore(startDate)) ? startDate : endDate;
        this.memo = memo;
    }

    /** 여행 일수(당일치기 = 1). 화면에 "n박 m일"로 보여줄 때 m에 해당한다. */
    public long days() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public boolean isParty() { return source == TripSource.PARTY; }

    /** SOLO 여행만 호출한다 - PARTY 여행은 MyTripService가 여기 오기 전에 막는다. */
    public void update(String title, String destination, LocalDate startDate, LocalDate endDate, String memo) {
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = (endDate == null || endDate.isBefore(startDate)) ? startDate : endDate;
        this.memo = memo;
    }
}
