package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.MyTripRequest;
import net.datasa.tanoshimi.domain.dto.PostSnapView;
import net.datasa.tanoshimi.domain.entity.MyTripEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.TripSource;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.MyTripRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "내 여행" CRUD + 파티 완료 자동 등록. 담당: 김민규(⑥). v19 신규 - MyTripEntity 클래스 주석 참고.
 *
 * <p>TitleService.syncTitles 가 그렇듯, 이 클래스도 원래는 파티 완료 시점
 * (PartyCompletionScheduler)에 한 번만 반영되는 게 맞지만 그 스케줄러는 ⑤(허수연) 소유라
 * 임의로 고치지 않았다. syncFromCompletedParties 는 멱등이라 마이페이지를 열 때마다 불러도
 * 안전하다.
 *
 * <p><b>v19-4 변경</b> - 파티를 만들고 완료 처리만 해두면(실제로 안 가도) 여행 기록이 남는
 * 문제가 있어, {@link #isCountable} 로 "실제로 카운트할" 여행을 가려낸다. SOLO 여행은 등록
 * 자체가 근거라 항상 카운트되고, PARTY 여행은 그 여행에 연결된 스냅이 최소 1장 있어야
 * 카운트된다(2026-09-01 요청). 목록(listMine)에는 스냅이 없는 PARTY 여행도 그대로 보여준다 -
 * 사용자가 "아직 인증 안 됨"을 보고 스냅을 올릴 수 있어야 하므로, 화면에서 숨기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MyTripService {

    private final MyTripRepository myTripRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PostRepository postRepository;

    /**
     * 완료된 파티 중 아직 "내 여행"에 없는 것을 채워 넣는다.
     * existsByUserAndParty 로 중복 생성을 막으므로 여러 번 호출해도 안전하다.
     */
    @Transactional
    public void syncFromCompletedParties(UserEntity user) {
        List<PartyEntity> completed =
                partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed);
        for (PartyEntity party : completed) {
            if (myTripRepository.existsByUserAndParty(user, party)) {
                continue;
            }
            myTripRepository.save(MyTripEntity.builder()
                    .user(user).source(TripSource.PARTY).party(party)
                    .title(party.getTitle())
                    .destination(party.getRegion())
                    .startDate(party.getDepartureDate())
                    .endDate(party.endDate())
                    .build());
        }
    }

    /** 파티 동기화까지 마친 뒤, 최신 여행이 위로 오게 정렬된 전체 목록(스냅 없는 PARTY 여행 포함). */
    @Transactional
    public List<MyTripEntity> listMine(UserEntity user) {
        syncFromCompletedParties(user);
        return myTripRepository.findByUserOrderByStartDateDesc(user);
    }

    /**
     * 이 여행이 여행 횟수·지도·칭호 집계에 실제로 들어가도 되는지. SOLO 여행은 사용자가
     * 직접 등록한 것 자체가 근거라 항상 true. PARTY 여행은 파티 완료만으로는 부족하고,
     * 그 여행에 연결된(post.trip = 이 여행) 블라인드 안 된 스냅이 최소 1장 있어야 true다 -
     * 그래야 파티만 만들고 완료 처리한 뒤 실제로 안 간 여행이 기록되지 않는다.
     *
     * <p><b>[v20-4]</b> post.trip 이 채워지는 경로는 오직 하나다 - 글쓰기 모달에서 "여행
     * 선택"으로 이 여행을 고르는 것(PostService.write 의 tripId 처리). 이미 올려둔 스냅을
     * 나중에 소급 연결해주는 기능은(v20-2) 만들었다가 "구제하지 말고 정방향(여행 등록 →
     * 글쓰기에서 선택)만 인정하라"는 요청으로 걷어냈다 - 과거에 tripId 없이 올라간 스냅은
     * 앞으로도 계속 이 여행의 근거가 되지 못한다(의도된 동작).
     */
    @Transactional(readOnly = true)
    public boolean isCountable(MyTripEntity trip) {
        return !trip.isParty() || postRepository.existsByTripAndBlindedFalse(trip);
    }

    /**
     * [v20-7 신규] "내 여행" 관리 화면의 "📷 스냅 보기" 버튼 - 이 여행에 연결된(post.trip=
     * 이 여행, 블라인드 제외) 스냅 전부를 최신순으로 돌려준다. 파티 자동 등록 여행이든
     * 직접 등록(SOLO) 여행이든 가리지 않는다 - 조회는 수정/삭제(findOwned 만 쓰고 파티
     * 여부는 따로 안 막음)와 달리 소유권만 확인하면 된다. isCountable=false(스냅 인증
     * 대기)인 파티 여행에 대해 불러도 빈 목록이 정상 응답이다(연결된 스냅이 없다는 뜻
     * 그대로).
     */
    @Transactional(readOnly = true)
    public List<PostSnapView> snapsForTrip(UserEntity user, Long tripId) {
        MyTripEntity trip = findOwned(user, tripId);
        return postRepository.findByTripAndBlindedFalseOrderByCreatedAtDesc(trip)
                .stream().map(PostSnapView::of).toList();
    }

    @Transactional
    public MyTripEntity create(UserEntity user, MyTripRequest req) {
        validateDates(req.startDate(), req.endDate());
        MyTripEntity trip = MyTripEntity.builder()
                .user(user).source(TripSource.SOLO).party(null)
                .title(req.title().trim())
                .destination(req.destination().trim())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .memo(blankToNull(req.memo()))
                .build();
        return myTripRepository.save(trip);
    }

    @Transactional
    public MyTripEntity update(UserEntity user, Long id, MyTripRequest req) {
        MyTripEntity trip = findOwned(user, id);
        if (trip.isParty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "파티 완료로 자동 등록된 여행은 직접 수정할 수 없어요.");
        }
        validateDates(req.startDate(), req.endDate());
        trip.update(req.title().trim(), req.destination().trim(), req.startDate(), req.endDate(),
                blankToNull(req.memo()));
        return trip;
    }

    @Transactional
    public void delete(UserEntity user, Long id) {
        MyTripEntity trip = findOwned(user, id);
        if (trip.isParty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "파티 완료로 자동 등록된 여행은 삭제할 수 없어요.");
        }
        myTripRepository.delete(trip);
    }

    private MyTripEntity findOwned(UserEntity user, Long id) {
        return myTripRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "여행을 찾을 수 없습니다."));
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "여행 날짜를 입력해 주세요.");
        }
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "돌아오는 날짜는 가는 날짜보다 빠를 수 없어요.");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
