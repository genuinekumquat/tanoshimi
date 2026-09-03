package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.PartyCardView;
import net.datasa.tanoshimi.domain.dto.PartyCreateRequest;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파티 만들기.
 * 파티를 만든 사람은 자동으로 owner 로 party_members 에 등록되고,
 * 파티 전용 채팅방(chat_rooms, type=party)도 함께 생성된다 - data.sql 의 더미 파티들과
 * 똑같은 구조를 서비스 코드로도 재현한 것.
 *
 * <p>계획표(trip_schedules)도 이 시점에 바로 만든다 - "항공/숙박 예약 전에도 미리 계획을
 * 짜볼 수 있게" 하기 위해서다. 아직 reservation 이 없는 초안 상태이며, 나중에 패키지를
 * 예약하면 ReservationService 가 이 계획표에 reservation 을 연결한다.
 */
@Service
@RequiredArgsConstructor
public class PartyService {

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final TourRepository tourRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final NotificationService notificationService;
    private final MannerTempService mannerTempService;
    private final FileStorageService fileStorageService;

    /** 메인 페이지 "모집 마감 임박" 카드의 출발일 표기 포맷. */
    private static final DateTimeFormatter URGENT_CARD_DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // ---------------------------------------------------------------- 조회 (컨트롤러가 Repository 를 직접 부르지 않도록 여기로 모음)

    /** 파티 1건. 없으면 PARTY_NOT_FOUND. 블라인드 여부는 공개 화면에서만 따지므로 여기선 보지 않는다. */
    @Transactional(readOnly = true)
    public PartyEntity getParty(Long id) {
        return partyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
    }

    /** 공개 상세/신청 화면용 - 존재 + 블라인드 아님까지 확인한다. */
    @Transactional(readOnly = true)
    public PartyEntity getVisibleParty(Long id) {
        PartyEntity party = getParty(id);
        if (party.isBlinded()) {
            throw new BusinessException(ErrorCode.PARTY_NOT_FOUND, "블라인드 처리된 파티입니다.");
        }
        return party;
    }

    /** 파티원 목록. */
    @Transactional(readOnly = true)
    public List<PartyMemberEntity> members(PartyEntity party) {
        return partyMemberRepository.findByParty(party);
    }

    /** 내가 속한 파티(가입일 최신순) - 마이페이지 / 내 파티 목록 공용. */
    @Transactional(readOnly = true)
    public List<PartyMemberEntity> myMemberships(UserEntity user) {
        return partyMemberRepository.findByUserOrderByJoinedAtDesc(user);
    }

    /** 파티원 중 지정한 유저(보통 본인)를 뺀 나머지 유저 목록 - 계획표 편집권 위임 대상 등. */
    @Transactional(readOnly = true)
    public List<UserEntity> otherMembers(PartyEntity party, Long excludeUserId) {
        return partyMemberRepository.findByParty(party).stream()
                .filter(m -> !m.getUser().getId().equals(excludeUserId))
                .map(PartyMemberEntity::getUser)
                .toList();
    }

    /** URL 만 알고 들어온 비파티원을 막는 최종 방어선. 파티 전용 화면 진입 시 호출. */
    @Transactional(readOnly = true)
    public void assertMember(PartyEntity party, UserEntity user) {
        if (!partyMemberRepository.existsByPartyAndUser(party, user)) {
            throw new BusinessException(ErrorCode.NOT_PARTY_MEMBER);
        }
    }

    /** 파티 만들기 폼에서 고를 수 있는 패키지(투어) 목록. */
    @Transactional(readOnly = true)
    public List<TourEntity> selectableTours() {
        return tourRepository.findByStatusOrderByIdDesc(ActiveStatus.active);
    }

    /**
     * 메인 페이지 "모집 마감 임박" 카드 - 모집중이고 블라인드 아닌 파티를 잔여석 적은 순,
     * 같으면 출발일 빠른 순으로 정렬해서 카드 뷰로 변환한다.
     */
    @Transactional(readOnly = true)
    public List<PartyCardView> urgentPartyCards() {
        return partyRepository.findByStatusAndBlindedFalseOrderByDepartureDateAsc(PartyStatus.recruiting).stream()
                .map(p -> new PartyCardView(
                        p.getId(), p.getTitle(), p.getRegion(),
                        p.getDepartureDate().format(URGENT_CARD_DATE_FMT),
                        p.getBudgetKrw(), p.getCapacity(), (int) partyMemberRepository.countByParty(p),
                        p.getThumbnailUrl(), p.getStyleTag()))
                .sorted(Comparator.<PartyCardView>comparingInt(PartyCardView::remaining)
                        .thenComparing(PartyCardView::departureDate))
                .toList();
    }

    /**
     * 파티 게시판(둘러보기) 목록.
     *
     * <p>기본(includePast=false)은 "지금 신청할 수 있는" 파티만 보여준다 - status=recruiting 이고
     * 출발일이 아직 안 지난 파티. 출발일이 지난 파티는 모집상태 뱃지를 뭘로 바꾸든 아무도 신청할 수
     * 없는 죽은 글이라, 이 목록의 목적("같이 갈 사람 모집")에 맞지 않아 기본 조회에서 뺀다.
     *
     * <p>includePast=true("지난 모임 보기" 탭)면 출발일이 지난 파티를 상태 무관·최근 출발순으로
     * 보여준다. 이때는 지역/키워드 필터를 적용하지 않는다(단순 열람 용도).
     */
    @Transactional(readOnly = true)
    public List<PartyEntity> listBoard(String region, String keyword, boolean includePast) {
        LocalDate today = LocalDate.now();
        if (includePast) {
            return partyRepository.findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(today);
        }
        if (keyword != null && !keyword.isBlank()) {
            return partyRepository.searchRecruiting(PartyStatus.recruiting, keyword.trim(), today);
        }
        return (region == null || region.isBlank())
                ? partyRepository.findByStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                        PartyStatus.recruiting, today)
                : partyRepository.findByRegionAndStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                        region, PartyStatus.recruiting, today);
    }

    @Transactional
    public Long createParty(UserEntity owner, PartyCreateRequest req) {
        TourEntity tour = req.tourId() == null ? null : tourRepository.findById(req.tourId()).orElse(null);

        PartyEntity party = PartyEntity.builder()
                .owner(owner)
                .tour(tour)
                .title(req.title())
                .description(req.description())
                .region(req.region())
                .departureDate(req.departureDate())
                .durationDays((byte) (req.durationDays() <= 0 ? 1 : req.durationDays()))
                .budgetKrw(req.budgetKrw())
                .capacity((byte) req.capacity())
                .styleTag(req.styleTag())
                .genderRestriction(GenderRestriction.valueOf(req.genderRestriction()))
                .ageMin(req.ageMin() == null ? null : req.ageMin().byteValue())
                .ageMax(req.ageMax() == null ? null : req.ageMax().byteValue())
                .nationalityRestriction(NationalityRestriction.valueOf(req.nationalityRestriction()))
                .thumbnailUrl(req.thumbnailUrl())
                .build();
        partyRepository.save(party);
        fileStorageService.markActive(req.thumbnailUrl());

        partyMemberRepository.save(new PartyMemberEntity(party, owner, PartyMemberRole.owner));

        ChatRoomEntity room = chatRoomRepository.save(ChatRoomEntity.forParty(party));
        chatRoomMemberRepository.save(new ChatRoomMemberEntity(room, owner));

        tripScheduleRepository.save(new TripScheduleEntity(party));

        return party.getId();
    }

    /**
     * 계획표가 없으면 만들어서 반환한다.
     * data.sql 로 미리 넣어둔 더미 파티처럼, 이 변경 이전에 만들어진 파티는 계획표가 없을 수 있어서
     * 화면(PartyRoomController)에서 조회할 때마다 이 메서드로 "없으면 즉시 생성"해 자연스럽게 맞춰준다.
     */
    @Transactional
    public TripScheduleEntity ensureSchedule(PartyEntity party) {
        return tripScheduleRepository.findByParty(party)
                .orElseGet(() -> tripScheduleRepository.save(new TripScheduleEntity(party)));
    }

    @Transactional
    public void updateParty(Long partyId, UserEntity owner, PartyCreateRequest req) {
        PartyEntity party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (!party.getOwner().getId().equals(owner.getId()) && !owner.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "파티장만 파티 정보를 수정할 수 있습니다.");
        }
        TourEntity tour = req.tourId() == null ? null : tourRepository.findById(req.tourId()).orElse(null);
        party.updateInfo(
                tour,
                req.title(),
                req.description(),
                req.region(),
                req.departureDate(),
                (byte) (req.durationDays() <= 0 ? 1 : req.durationDays()),
                req.budgetKrw(),
                (byte) req.capacity(),
                req.styleTag(),
                GenderRestriction.valueOf(req.genderRestriction()),
                req.ageMin() == null ? null : req.ageMin().byteValue(),
                req.ageMax() == null ? null : req.ageMax().byteValue(),
                NationalityRestriction.valueOf(req.nationalityRestriction())
        );
        if (req.thumbnailUrl() != null && !req.thumbnailUrl().isBlank()) {
            party.changeThumbnail(req.thumbnailUrl());
            fileStorageService.markActive(req.thumbnailUrl());
        }
    }

    @Transactional
    public void closeParty(Long partyId, UserEntity owner) {
        PartyEntity party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (!party.getOwner().getId().equals(owner.getId()) && !owner.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "파티장만 파티를 마감할 수 있습니다.");
        }
        party.close();
    }

    @Transactional
    public void deleteParty(Long partyId, UserEntity owner) {
        PartyEntity party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (!party.getOwner().getId().equals(owner.getId()) && !owner.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "파티장만 파티를 삭제할 수 있습니다.");
        }
        // 관련된 계획표, 채팅방, 지원서, 멤버 등은 DB CASCADE 설정에 따라 삭제되거나, 수동 삭제 필요.
        // 현재 JPA cascade 설정이 안되어있을 수 있으므로 직접 의존성을 지워주는 것도 고려할 수 있음.
        // 여기서는 가장 뼈대가 되는 partyRepository.delete(party) 만 먼저 호출.
        partyRepository.delete(party);
    }

    /**
     * 파티 탈퇴 - 파티장은 탈퇴할 수 없다(파티장이 나가려면 파티를 마감/삭제하거나
     * 다음 단계에서 "파티장 위임" 기능을 붙여야 한다 - 지금은 범위 밖).
     * party_members 와 chat_room_members 양쪽에서 같이 빠져야 한다(안 그러면 나갔는데
     * 채팅방엔 계속 남아있는 이상한 상태가 됨).
     */
    @Transactional
    public void leaveParty(Long partyId, UserEntity user) {
        PartyEntity party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (party.getOwner().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파티장은 탈퇴할 수 없습니다. 파티 마감/삭제를 이용해 주세요.");
        }
        PartyMemberEntity membership = partyMemberRepository.findByPartyAndUser(party, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTY_MEMBER));
        partyMemberRepository.delete(membership);

        chatRoomRepository.findByParty(party).ifPresent(room ->
                chatRoomMemberRepository.findByRoomAndUser(room, user)
                        .ifPresent(chatRoomMemberRepository::delete));

        if (party.getStatus() == PartyStatus.full) {
            party.reopen();
        }

        // [v16 신규] 중도이탈 매너온도 -0.5
        mannerTempService.applyLeaveOrKickPenalty(user, party.getId());
    }

    /**
     * 강퇴 - 파티장만 할 수 있고, 파티장 본인은 강퇴 대상이 될 수 없다(파티장이 파티를 나가려면
     * 마감/삭제를 쓰거나, 다음 단계에서 "파티장 위임" 기능이 필요하다 - 지금은 범위 밖).
     * leaveParty 와 거의 같은 정리 작업(party_members, chat_room_members 동시 제거)을 하되,
     * 강퇴당한 사람에게 알림을 보낸다는 점이 다르다.
     */
    @Transactional
    public void kickMember(Long partyId, UserEntity owner, Long targetUserId) {
        PartyEntity party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (!party.getOwner().getId().equals(owner.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "파티장만 강퇴할 수 있습니다.");
        }
        if (targetUserId.equals(owner.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파티장 본인은 강퇴할 수 없습니다.");
        }
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        PartyMemberEntity membership = partyMemberRepository.findByPartyAndUser(party, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTY_MEMBER));
        partyMemberRepository.delete(membership);

        chatRoomRepository.findByParty(party).ifPresent(room ->
                chatRoomMemberRepository.findByRoomAndUser(room, target)
                        .ifPresent(chatRoomMemberRepository::delete));

        if (party.getStatus() == PartyStatus.full) {
            party.reopen();
        }

        notificationService.notify(target, "party_kicked",
                "파티에서 내보내졌어요",
                "'" + party.getTitle() + "' 파티장이 회원님을 파티에서 내보냈습니다.",
                "/party-board");

        // [v16 신규] 강퇴 매너온도 -0.5 (본인 귀책이 아니어도 동일 규칙 적용 - 필드제약조건 확정 사항)
        mannerTempService.applyLeaveOrKickPenalty(target, party.getId());
    }
}
