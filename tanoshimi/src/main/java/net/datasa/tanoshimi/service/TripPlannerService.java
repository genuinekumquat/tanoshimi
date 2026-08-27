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
 * Service for managing trip plans and completing payment streams.
 */
@Service
@RequiredArgsConstructor
public class TripPlannerService {

    private final TripScheduleRepository scheduleRepository;
    private final TripScheduleItemRepository itemRepository;
    private final TripSchedulePaymentRepository paymentRepository;
    private final ActivityRepository activityRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final UserRepository userRepository;
    private final TripPlannerLockService lockService;

    @Transactional
    public void initializeDefaults(TripScheduleEntity schedule, UserEntity creator) {
        // [v16] 예약(reservation) 기능이 화면에서 빠지면서, 이제 예약 없이도 파티가 만들어질 수 있다.
        // reservation 이 있으면(레거시 호환) 그쪽 tour 를, 없으면 파티에 직접 연결된 tour 를 사용한다.
        // 둘 다 없으면(패키지 없이 순수 계획표만 쓰는 경우) 기본 항공/체크인 블록 없이 빈 계획표로 시작한다.
        TourEntity tour = schedule.getReservation() != null ? schedule.getReservation().getTour()
                : (schedule.getParty() != null ? schedule.getParty().getTour() : null);
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

    @Transactional(readOnly = true)
    public List<ScheduleItemView> getItems(TripScheduleEntity schedule) {
        return itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule).stream()
                .map(i -> new ScheduleItemView(
                        i.getId(), i.getDayIndex(), i.getStartMinute(), i.getDurationMinute(),
                        i.getSource().name(), i.getTitle(), i.getMemo(), i.getPriceKrw(), i.getPriceJpy(),
                        i.getAddedBy() != null ? i.getAddedBy().getId() : null,
                        i.getAddedBy() != null ? i.getAddedBy().getName() : "Unknown"))
                .toList();
    }

    @Transactional
    public Long addItem(TripScheduleEntity schedule, UserEntity user, ScheduleItemRequest req) {
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

    @Transactional
    public void submitForPayment(TripScheduleEntity schedule) {

        List<TripScheduleItemEntity> items = itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
        int totalKrw = items.stream().filter(i -> i.getSource() == ScheduleItemSource.activity)
                .mapToInt(TripScheduleItemEntity::getPriceKrw).sum();
        int totalJpy = items.stream().filter(i -> i.getSource() == ScheduleItemSource.activity)
                .mapToInt(TripScheduleItemEntity::getPriceJpy).sum();

        PartyEntity party = resolveParty(schedule);
        List<UserEntity> members = resolveMembers(schedule, party);

        long krwPayerCount = members.stream().filter(m -> m.getNationality() != Nationality.JP).count();
        long jpyPayerCount = members.size() - krwPayerCount;

        int krwShare = krwPayerCount == 0 ? 0 : totalKrw / (int) krwPayerCount;
        int krwRemainder = krwPayerCount == 0 ? 0 : totalKrw % (int) krwPayerCount;
        int jpyShare = jpyPayerCount == 0 ? 0 : totalJpy / (int) jpyPayerCount;
        int jpyRemainder = jpyPayerCount == 0 ? 0 : totalJpy % (int) jpyPayerCount;

        boolean krwRemainderGiven = false;
        boolean jpyRemainderGiven = false;

        for (UserEntity member : members) {
            boolean isJpy = member.getNationality() == Nationality.JP;
            int share = isJpy ? jpyShare : krwShare;
            int amount = share;

            if (!isJpy && krwRemainder > 0 && !krwRemainderGiven) {
                amount += krwRemainder;
                krwRemainderGiven = true;
            } else if (isJpy && jpyRemainder > 0 && !jpyRemainderGiven) {
                amount += jpyRemainder;
                jpyRemainderGiven = true;
            }

            if (amount > 0) {
                paymentRepository.save(new TripSchedulePaymentEntity(
                        schedule, member, isJpy ? Currency.JPY : Currency.KRW, amount));
            }
        }
        schedule.submit();
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void pay(TripScheduleEntity schedule, UserEntity payer) {
        TripSchedulePaymentEntity payment = paymentRepository.findByScheduleAndUser(schedule, payer)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payer.deductPoints(payment.getCurrency(), payment.getAmount())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
        }
        payment.markPaid();
        userRepository.save(payer);

        boolean allPaid = paymentRepository.findBySchedule(schedule).stream()
                .allMatch(p -> p.getStatus() == PaymentStatus.paid);
        if (allPaid) {
            schedule.confirm();
            scheduleRepository.save(schedule);
        }
    }

    private short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }

    private PartyEntity resolveParty(TripScheduleEntity schedule) {
        if (schedule.getParty() != null) return schedule.getParty();
        if (schedule.getReservation() != null) return schedule.getReservation().getParty();
        return null;
    }

    private List<UserEntity> resolveMembers(TripScheduleEntity schedule, PartyEntity party) {
        if (party != null) {
            return partyMemberRepository.findByParty(party).stream().map(PartyMemberEntity::getUser).toList();
        }
        if (schedule.getReservation() != null) {
            return List.of(schedule.getReservation().getBookedBy());
        }
        return List.of();
    }
}
