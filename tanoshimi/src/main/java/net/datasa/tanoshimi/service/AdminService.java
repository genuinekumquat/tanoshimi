package net.datasa.tanoshimi.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.ReportEntity;
import net.datasa.tanoshimi.domain.entity.ReportStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.repository.ReportRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 화면(회원/파티/신고 관리)의 조회·상태변경. 신고 처리 자체는 ReportService,
 * 배너는 BannerService 가 담당한다 - 여기서는 회원·파티 관리와 목록 페이징만.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Sort BY_ID_DESC = Sort.by(Sort.Direction.DESC, "id");
    private static final int PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public Page<UserEntity> pageUsers(String keyword, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, BY_ID_DESC);
        return (keyword == null || keyword.trim().isEmpty())
                ? userRepository.findAll(pageable)
                : userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword, pageable);
    }

    @Transactional(readOnly = true)
    public long pendingReportCount() {
        return reportRepository.countByStatus(ReportStatus.pending);
    }

    @Transactional(readOnly = true)
    public Page<ReportEntity> pagePendingReports(int page) {
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.pending, PageRequest.of(page, PAGE_SIZE));
    }

    /** 관리자 파티 목록 - owner 프록시를 미리 초기화해 화면 렌더링 중 LazyInitializationException 이 나지 않게 한다. */
    @Transactional(readOnly = true)
    public Page<PartyEntity> pageParties(String keyword, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, BY_ID_DESC);
        Page<PartyEntity> parties = (keyword == null || keyword.trim().isEmpty())
                ? partyRepository.findAll(pageable)
                : partyRepository.findByTitleContainingIgnoreCase(keyword, pageable);
        parties.forEach(p -> {
            if (p.getOwner() != null) {
                p.getOwner().getName();
            }
        });
        return parties;
    }

    /**
     * 유저 정지. 자기 자신은 정지할 수 없다(무시). 존재하지 않는 id 도 조용히 무시한다(기존 동작 유지).
     * duration: 999 = 영구(999년), 1 = 24시간, 그 외 = 그 일수만큼.
     */
    @Transactional
    public void suspendUser(Long userId, int duration, Long adminId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getId().equals(adminId)) return;
            LocalDateTime until = LocalDateTime.now();
            if (duration == 999) {
                until = until.plusYears(999);
            } else if (duration == 1) {
                until = until.plusHours(24);
            } else {
                until = until.plusDays(duration);
            }
            user.suspend(until);
        });
    }

    @Transactional
    public void activateUser(Long userId) {
        userRepository.findById(userId).ifPresent(UserEntity::activate);
    }

    @Transactional
    public void grantAdmin(Long userId) {
        userRepository.findById(userId).ifPresent(UserEntity::grantAdmin);
    }

    /** 관리자 권한 회수. 자기 자신은 회수할 수 없다(무시). */
    @Transactional
    public void revokeAdmin(Long userId, Long adminId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (!user.getId().equals(adminId)) {
                user.revokeAdmin();
            }
        });
    }

    @Transactional
    public void closeParty(Long partyId) {
        partyRepository.findById(partyId).ifPresent(PartyEntity::close);
    }
}
