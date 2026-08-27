package net.datasa.tanoshimi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.datasa.tanoshimi.domain.dto.ScheduleItemSnapshotEntry;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.ScheduleItemSource;
import net.datasa.tanoshimi.domain.entity.SnapshotTrigger;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleItemEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleSnapshotEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.repository.TripScheduleItemRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.TripScheduleSnapshotRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [v16 신규] 협업 일정 계획표의 편집권(lock) · 자동/수동 저장 · 스냅샷 롤백 담당.
 * 담당: 정태웅(②). 필드제약조건 확정 사항:
 * <ul>
 *   <li>편집권은 파티장만 파티원별로 부여/회수 가능, 한 번에 한 명만 가질 수 있다.</li>
 *   <li>자동저장 10분 주기 + 수동저장 버튼 병행, 저장마다 스냅샷 1건 생성.</li>
 *   <li>스냅샷 롤백은 파티장만 실행 가능, 롤백 시 현재 상태도 먼저 스냅샷으로 보존 후 복원.</li>
 * </ul>
 * 자동저장의 "10분 주기"는 서버가 전체 계획표를 배치로 훑는 방식이 아니라, 플래너 화면을
 * 열어둔 클라이언트가 10분마다 이 서비스의 save(trigger=auto) 를 호출하는 방식으로 구현한다
 * (실제로 열려있는 계획표만 저장되면 되므로 이쪽이 더 단순하고 효율적이다).
 */
@Service
@RequiredArgsConstructor
public class TripPlannerLockService {

    private final TripScheduleRepository scheduleRepository;
    private final TripScheduleItemRepository itemRepository;
    private final TripScheduleSnapshotRepository snapshotRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** 파티장이 특정 파티원에게 편집권을 부여한다. */
    @Transactional
    public void grantLock(Long scheduleId, UserEntity owner, Long targetUserId) {
        TripScheduleEntity schedule = fetchSchedule(scheduleId);
        assertPartyOwner(schedule, owner);
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        schedule.setLockedBy(target);
        scheduleRepository.save(schedule);
    }

    /** 파티장이 편집권을 회수한다 - 기본값인 "파티장 본인"에게 되돌아간다(null 이 아님). */
    @Transactional
    public void revokeLock(Long scheduleId, UserEntity owner) {
        TripScheduleEntity schedule = fetchSchedule(scheduleId);
        assertPartyOwner(schedule, owner);
        schedule.setLockedBy(owner);
        scheduleRepository.save(schedule);
    }

    /**
     * 이 사용자가 지금 계획표를 편집할 수 있는지 검사한다. TripPlannerService 의
     * addItem/resizeItem/removeItem 등 편집성 메서드는 반드시 이 메서드를 먼저 호출해야 한다.
     * NULL이면 전원 읽기전용(자유편집 아님)이므로 파티장이라도 lock 을 스스로에게 부여해야 편집 가능하다.
     */
    public void assertCanEdit(TripScheduleEntity schedule, Long userId) {
        if (!schedule.isLockedBy(userId)) {
            throw new BusinessException(ErrorCode.LOCK_NOT_HELD);
        }
    }

    /** 수동 저장 버튼 또는 10분 주기 자동저장 호출 시 사용 - 현재 상태를 스냅샷으로 남긴다. */
    @Transactional
    public void save(Long scheduleId, UserEntity actor, SnapshotTrigger trigger) {
        TripScheduleEntity schedule = fetchSchedule(scheduleId);
        takeSnapshot(schedule, actor, trigger);
        schedule.touchSaved();
        scheduleRepository.save(schedule);
    }

    /** 롤백 화면에 보여줄 저장 시점 목록(최신순). */
    @Transactional(readOnly = true)
    public List<TripScheduleSnapshotEntity> listSnapshots(Long scheduleId) {
        return snapshotRepository.findByScheduleOrderByCreatedAtDesc(fetchSchedule(scheduleId));
    }

    /**
     * 파티장 전용 롤백. "되돌리기의 되돌리기"가 가능하도록, 복원하기 직전에 현재 상태를
     * 먼저 스냅샷으로 보존한다(필드제약조건 확정 사항) - 그래서 롤백해봤다가 마음이 바뀌어도
     * 롤백 직전 시점으로 다시 돌아올 수 있다.
     */
    @Transactional
    @SneakyThrows
    public void rollback(Long scheduleId, Long snapshotId, UserEntity owner) {
        TripScheduleEntity schedule = fetchSchedule(scheduleId);
        assertPartyOwner(schedule, owner);

        TripScheduleSnapshotEntity target = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND));
        if (!target.getSchedule().getId().equals(schedule.getId())) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND);
        }

        // 1. 복원 직전 현재 상태를 먼저 보존
        takeSnapshot(schedule, owner, SnapshotTrigger.manual);

        // 2. 대상 스냅샷의 JSON을 파싱해 전체 아이템을 교체
        List<ScheduleItemSnapshotEntry> entries = objectMapper.readValue(
                target.getSnapshotData(), new TypeReference<List<ScheduleItemSnapshotEntry>>() {});

        itemRepository.deleteBySchedule(schedule);
        for (ScheduleItemSnapshotEntry entry : entries) {
            ActivityEntity activity = entry.activityId() == null ? null
                    : activityRepository.findById(entry.activityId()).orElse(null);
            UserEntity addedBy = entry.addedByUserId() == null ? null
                    : userRepository.findById(entry.addedByUserId()).orElse(null);

            TripScheduleItemEntity item = TripScheduleItemEntity.builder()
                    .schedule(schedule)
                    .activity(activity)
                    .dayIndex((byte) entry.dayIndex())
                    .startMinute((short) entry.startMinute())
                    .durationMinute((short) entry.durationMinute())
                    .source(ScheduleItemSource.valueOf(entry.source()))
                    .title(entry.title())
                    .memo(entry.memo())
                    .priceKrw(entry.priceKrw())
                    .priceJpy(entry.priceJpy())
                    .addedBy(addedBy)
                    .build();
            item.restoreFixedFlag(entry.isFixed());
            itemRepository.save(item);
        }

        schedule.touchSaved();
        scheduleRepository.save(schedule);
    }

    @SneakyThrows
    private void takeSnapshot(TripScheduleEntity schedule, UserEntity actor, SnapshotTrigger trigger) {
        List<TripScheduleItemEntity> items = itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
        List<ScheduleItemSnapshotEntry> entries = items.stream()
                .map(i -> new ScheduleItemSnapshotEntry(
                        i.getActivity() == null ? null : i.getActivity().getId(),
                        i.getDayIndex(), i.getStartMinute(), i.getDurationMinute(),
                        i.getSource().name(), i.isFixed(), i.getTitle(), i.getMemo(),
                        i.getPriceKrw() == null ? 0 : i.getPriceKrw(),
                        i.getPriceJpy() == null ? 0 : i.getPriceJpy(),
                        i.getAddedBy() == null ? null : i.getAddedBy().getId()))
                .collect(Collectors.toList());
        String json = objectMapper.writeValueAsString(entries);
        snapshotRepository.save(new TripScheduleSnapshotEntity(schedule, json, trigger, actor));
    }

    private TripScheduleEntity fetchSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void assertPartyOwner(TripScheduleEntity schedule, UserEntity user) {
        PartyEntity party = schedule.getParty();
        if (party == null || !party.getOwner().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "파티장만 할 수 있습니다.");
        }
    }
}
