package net.datasa.tanoshimi.service;

import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ScheduleItemRequest;
import net.datasa.tanoshimi.domain.dto.ScheduleItemView;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing trip plans.
 */
@Service
@RequiredArgsConstructor
public class TripPlannerService {

    private final TripScheduleRepository scheduleRepository;
    private final TripScheduleItemRepository itemRepository;
    private final ActivityRepository activityRepository;
    private final TripPlannerLockService lockService;

    @Transactional
    public void initializeDefaults(TripScheduleEntity schedule, UserEntity creator) {
        // 파티에 연결된 tour 가 있으면 기본 항공/체크인 블록을 깐다.
        // tour 가 없으면(패키지 없이 순수 계획표만 쓰는 경우) 빈 계획표로 시작한다.
        TourEntity tour = schedule.getParty() != null ? schedule.getParty().getTour() : null;
        if (tour == null) {
            return;
        }
        int nights = tour.getDurationNights();

        if (tour.getArrTime() != null) {
            itemRepository.save(TripScheduleItemEntity.builder()
                    .schedule(schedule).dayIndex((byte) 1)
                    .startMinute(toMinute(tour.getArrTime()))
                    .durationMinute((short) 90)
                    .source(ScheduleItemSource.package_default)
                    .title("공항 버스 이동")
                    .priceKrw(0).priceJpy(0)
                    .addedBy(creator)
                    .build());
        }
        itemRepository.save(TripScheduleItemEntity.builder()
                .schedule(schedule).dayIndex((byte) 1)
                .startMinute(toMinute(tour.getCheckinTime()))
                .durationMinute((short) 60)
                .source(ScheduleItemSource.package_default)
                .title("숙소 체크인")
                .priceKrw(0).priceJpy(0)
                .addedBy(creator)
                .build());

        int lastDay = nights + 1;
        short checkoutStart = (short) Math.max(0, toMinute(tour.getCheckoutTime()) - 60);
        itemRepository.save(TripScheduleItemEntity.builder()
                .schedule(schedule).dayIndex((byte) lastDay)
                .startMinute(checkoutStart)
                .durationMinute((short) 60)
                .source(ScheduleItemSource.package_default)
                .title("숙소 체크아웃")
                .priceKrw(0).priceJpy(0)
                .addedBy(creator)
                .build());

        if (tour.getDepTime() != null) {
            itemRepository.save(TripScheduleItemEntity.builder()
                    .schedule(schedule).dayIndex((byte) lastDay)
                    .startMinute(toMinute(tour.getCheckoutTime()))
                    .durationMinute((short) 120)
                    .source(ScheduleItemSource.package_default)
                    .title("공항 이동")
                    .priceKrw(0).priceJpy(0)
                    .addedBy(creator)
                    .build());
        }
    }

    // ---------------------------------------------------------------- 조회 (컨트롤러가 Repository 를 직접 부르지 않도록 여기로 모음)

    /** 계획표 1건. 없으면 SCHEDULE_NOT_FOUND. */
    @Transactional(readOnly = true)
    public TripScheduleEntity getSchedule(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    /** 계획표 1건 + party/tour 까지 fetch (플래너 화면·AI 기능이 tour 정보를 바로 씀). */
    @Transactional(readOnly = true)
    public TripScheduleEntity getScheduleWithContext(Long id) {
        return scheduleRepository.findWithContextById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    /**
     * 저장된 일정 항목 원본(엔티티) - AI 검증/리포트가 항목 id·GPS·activity 를 직접 다뤄야 해서
     * 화면용 View(getItems) 가 아니라 엔티티가 필요하다.
     */
    @Transactional(readOnly = true)
    public List<TripScheduleItemEntity> rawItems(TripScheduleEntity schedule) {
        return itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
    }

    /** AI 검증이 새 일정으로 갈아끼우기 전, 고정(package_default) 아닌 항목을 전부 지운다. */
    @Transactional
    public void clearNonFixedItems(TripScheduleEntity schedule) {
        for (TripScheduleItemEntity it : itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule)) {
            if (!it.isFixed()) {
                itemRepository.delete(it);
            }
        }
        itemRepository.flush();
    }

    /**
     * AI 검증이 돌려준 새 일정으로 고정 아닌 항목을 갈아끼운다. 지우기와 추가를 한 트랜잭션으로
     * 묶어서, 중간에 하나라도 실패(편집권 없음·없는 활동 등)하면 지운 것까지 전부 되돌린다 -
     * 예전엔 지우기만 커밋되고 추가는 실패해서 일정이 통째로 날아가는 경우가 있었다.
     */
    @Transactional
    public void replaceNonFixedItems(TripScheduleEntity schedule, UserEntity user, List<ScheduleItemRequest> reqs) {
        lockService.assertCanEdit(schedule, user.getId());
        clearNonFixedItems(schedule);
        for (ScheduleItemRequest req : reqs) {
            addItem(schedule.getId(), user, req);
        }
    }

    @Transactional(readOnly = true)
    public List<ScheduleItemView> getItems(TripScheduleEntity schedule) {
        return itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule).stream()
                .map(i -> new ScheduleItemView(
                        i.getId(), i.getDayIndex(), i.getStartMinute(), i.getDurationMinute(),
                        i.getSource().name(), i.getTitle(), i.getMemo(), i.getColor(), i.getPriceKrw(), i.getPriceJpy(),
                        i.getAddedBy() != null ? i.getAddedBy().getId() : null,
                        i.getAddedBy() != null ? i.getAddedBy().getName() : "Unknown"))
                .toList();
    }

    /**
     * 일정 제목 끝에 붙는 "활동/이벤트" 표현을 더 안 붙을 때까지 반복해서 잘라내고 장소명만 남긴다
     * (예: "쿠로몬 시장 먹거리 투어" -> "쿠로몬 시장", "오사카 스테이션 시티 가을 축제 미식 탐방" ->
     * "오사카역"). 괄호 안(AI 의 "추천 활동: 우메다 츠타야 카페" 등)은 가게 하나를 콕 집어 찾다가
     * 엉뚱한 곳이 잡히므로 무시하고 제목 앞부분 키워드만 쓴다. 남은 게 "우메다"처럼 역 이름이 되는
     * 지역 하나뿐이면 "우메다역", "~ 스테이션 (시티)"는 "~역"으로 바꿔 역 기준으로 찾는다.
     * 목록에 없는 새 표현은 못 걸러내는 휴리스틱이라, 이상한 결과가 보이면 목록에 추가할 것.
     */
    private static final List<String> ACTIVITY_PHRASE_SUFFIXES = List.of(
            "하츠모데", "산책", "체험", "관람", "투어", "나들이", "먹방", "감상", "구경", "쇼핑", "축제",
            "야경", "먹거리", "미식", "탐방", "휴식", "방문", "출발", "식사", "점심", "저녁", "브런치",
            "가을", "여름", "겨울", "및", "체크인", "체크아웃", "복귀", "카페"
    );
    // ponytail: 역 기준으로 바꿀 지역 목록 - 새 지역이 "OO 오사카"로 엉뚱하게 잡히면 여기에 추가.
    private static final java.util.Set<String> STATION_AREAS = java.util.Set.of(
            "우메다", "난바", "신사이바시", "텐노지", "신오사카", "교토", "하카타", "텐진", "삿포로",
            "신주쿠", "시부야", "이케부쿠로", "우에노", "도쿄", "나고야", "고베", "산노미야"
    );
    private static final java.util.regex.Pattern STATION_SUFFIX =
            java.util.regex.Pattern.compile("\\s*스테이션(\\s*시티)?$");
    private static final java.util.regex.Pattern PARENS = java.util.regex.Pattern.compile("\\(([^)]*)\\)");
    private static final List<String> LODGING_WORDS = List.of("호텔", "숙소", "체크인", "체크아웃");

    static boolean isLodging(String title) {
        return LODGING_WORDS.stream().anyMatch(title::contains);
    }

    /**
     * 숙소 일정에 적힌 호텔 이름 - "도미인 난바 호텔 출발" -> "도미인 난바 호텔",
     * "호텔 출발 (도미인 난바)" -> "도미인 난바". 이름 없이 "호텔 출발"/"숙소 체크인"뿐이면 null.
     */
    static String lodgingName(String title) {
        if (!isLodging(title)) return null;
        java.util.regex.Matcher m = PARENS.matcher(title);
        if (m.find() && !m.group(1).isBlank() && m.group(1).indexOf(':') < 0) {
            return m.group(1).trim();
        }
        String name = extractPlaceName(title);
        return "호텔".equals(name) || "숙소".equals(name) ? null : name;
    }

    static String extractPlaceName(String title) {
        String result = PARENS.matcher(title).replaceAll(" ").trim();
        boolean cut = true;
        while (cut) {
            cut = false;
            for (String suffix : ACTIVITY_PHRASE_SUFFIXES) {
                if (result.length() > suffix.length() && result.endsWith(suffix)) {
                    String trimmed = result.substring(0, result.length() - suffix.length()).trim();
                    if (!trimmed.isEmpty()) {
                        result = trimmed;
                        cut = true;
                        break;
                    }
                }
            }
        }
        result = STATION_SUFFIX.matcher(result).replaceAll("역");
        return STATION_AREAS.contains(result) ? result + "역" : result;
    }

    /**
     * "지도로 보기"(planner/route-map) 화면용.
     * 장소 단건 "보기" 링크는 좌표가 있어도 이름 검색(mapQuery)을 쓴다 - 유명 관광지는 좌표 핀보다
     * 이름으로 찾아야 리뷰/사진이 딸린 정상적인 장소 카드가 뜨기 때문. 구간별 "길찾기"도 같은
     * mapQuery 로 경로를 찾는다(좌표 없는 일정도 이어지도록). 커스텀 일정(AI 추천 반영 포함)도 제목에서 장소명을
     * 추출해 검색한다 - "쿠로몬 시장 먹거리 투어 오사카"로 검색하면 시장 대신 투어 상품이 잡힌다.
     */
    @Transactional(readOnly = true)
    public List<net.datasa.tanoshimi.domain.dto.RouteMapStopView> getRouteMapStops(TripScheduleEntity schedule) {
        String tourRegion = schedule.getParty() != null && schedule.getParty().getTour() != null
                ? schedule.getParty().getTour().getRegion() : null;
        List<TripScheduleItemEntity> items = rawItems(schedule);
        // 호텔 이름을 한 곳(예: 1일차 "호텔 출발 (도미인 난바)")에만 적어도 다른 숙소 일정(체크인·체크아웃·
        // 다른 날 "호텔 출발")이 그 이름으로 검색되게 한다.
        // ponytail: 처음 적힌 호텔 하나만 공유 - 숙소를 옮기는 일정이면 각 항목에 이름을 따로 적어야 한다.
        String hotelName = items.stream().map(i -> lodgingName(i.getTitle()))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return items.stream()
                .map(i -> {
                    ActivityEntity activity = i.getActivity();
                    boolean hasLocation = activity != null && activity.getLatitude() != null && activity.getLongitude() != null;
                    String region = activity != null && activity.getRegion() != null && !activity.getRegion().isBlank()
                            ? activity.getRegion() : tourRegion;
                    String namePart = extractPlaceName(i.getTitle());
                    if (isLodging(i.getTitle())) {
                        String own = lodgingName(i.getTitle());
                        if (own != null) namePart = own;
                        else if (hotelName != null) namePart = hotelName;
                    }
                    String mapQuery = namePart + (region != null && !region.isBlank() ? " " + region : "");
                    return new net.datasa.tanoshimi.domain.dto.RouteMapStopView(
                            i.getDayIndex(), i.getStartMinute(), i.getDurationMinute(),
                            i.getTitle(), i.getMemo(), hasLocation,
                            hasLocation ? activity.getLatitude().toPlainString() : null,
                            hasLocation ? activity.getLongitude().toPlainString() : null,
                            mapQuery);
                })
                .toList();
    }

    @Transactional
    public Long addItem(Long scheduleId, UserEntity user, ScheduleItemRequest req) {
        TripScheduleEntity schedule = scheduleRepository.findById(scheduleId).orElseThrow(() -> new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.SCHEDULE_NOT_FOUND));
        lockService.assertCanEdit(schedule, user.getId()); // [v16] 편집권 보유자만 추가 가능
        boolean isCustom = req.activityId() == null;
        

        String title = req.title();
        int priceKrw = 0, priceJpy = 0;
        ActivityEntity activity = null;
        if (!isCustom) {
            activity = activityRepository.findById(req.activityId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 활동입니다."));
            title = activity.getTitle();
            priceKrw = activity.getPriceKrw();
            priceJpy = activity.getPriceJpy();
        }

        TripScheduleItemEntity item = TripScheduleItemEntity.builder()
                .schedule(schedule)
                .dayIndex((byte) req.dayIndex())
                .startMinute((short) req.startMinute())
                .durationMinute((short) req.durationMinute())
                .source(isCustom ? ScheduleItemSource.custom : ScheduleItemSource.activity)
                .activity(activity)
                .title(title == null || title.isBlank() ? "이름없는 일정" : title)
                .memo(req.memo())
                .color(req.color())
                .priceKrw(priceKrw)
                .priceJpy(priceJpy)
                .addedBy(user)
                .build();
        return itemRepository.save(item).getId();
    }

    @Transactional
    public void resizeItem(Long itemId, Long userId, int newStartMinute, int newDurationMinute, Integer newDayIndex) {
        TripScheduleItemEntity item = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        lockService.assertCanEdit(item.getSchedule(), userId); // [v16] 편집권 보유자만 이동/리사이즈 가능
        if (item.isFixed()) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_DRAFT, "고정된 일정은 이동하거나 크기를 바꿀 수 없습니다.");
        }
        item.reschedule((short) newStartMinute, (short) newDurationMinute, newDayIndex != null ? newDayIndex.byteValue() : null);
        itemRepository.save(item);
    }

    @Transactional
    public void removeItem(Long itemId, Long userId) {
        TripScheduleItemEntity item = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        lockService.assertCanEdit(item.getSchedule(), userId); // [v16] 편집권 보유자만 삭제 가능
        if (item.isFixed()) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_DRAFT, "고정된 일정은 삭제할 수 없습니다.");
        }
        itemRepository.delete(item);
    }

    /** 여행 일수 변경 - 줄어든 날짜 이후에 걸린 일정 항목은 함께 삭제한다. */
    @Transactional
    public void updateDurationDays(TripScheduleEntity schedule, int days) {
        schedule.setDurationDays(days);
        scheduleRepository.save(schedule);

        for (TripScheduleItemEntity item : itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule)) {
            if (item.getDayIndex() > days) {
                itemRepository.delete(item);
            }
        }
        itemRepository.flush();
    }

    /** 계획표 최종 확정. draft 상태에서만 호출 가능하며 submitted -> confirmed 로 한 번에 넘긴다. */
    @Transactional
    public void finalizeSchedule(TripScheduleEntity schedule) {
        if (!schedule.isDraft()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 제출 완료된 시간표입니다.");
        }
        schedule.submit();  // 논리적 상태 전이
        schedule.confirm();
        scheduleRepository.save(schedule);
    }

    private short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }
}
