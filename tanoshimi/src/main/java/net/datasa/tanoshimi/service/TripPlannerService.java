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
