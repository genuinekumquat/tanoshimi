package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.ReportEntity;
import net.datasa.tanoshimi.domain.entity.ReportTargetType;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.repository.NotificationRepository;
import net.datasa.tanoshimi.domain.entity.Role;
import net.datasa.tanoshimi.domain.entity.NotificationEntity;
import java.util.List;
import net.datasa.tanoshimi.repository.PostRepository;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.domain.entity.PostEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.ReportActionTaken;
import net.datasa.tanoshimi.domain.entity.ReportStatus;

/** 신고 접수 + 관리자 처리(승인/기각). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final PartyRepository partyRepository;
    private final MannerTempService mannerTempService;

    @Transactional
    public void submit(UserEntity reporter, ReportTargetType targetType, Long targetId, String targetLabel, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 사유를 입력해 주세요.");
        }
        reportRepository.save(new ReportEntity(reporter, targetType, targetId, targetLabel, reason.trim()));
        
        String linkUrl = "";
        if (targetType == ReportTargetType.post) {
            linkUrl = "/board/" + targetId;
        } else if (targetType == ReportTargetType.party) {
            linkUrl = "/party-board/" + targetId;
        }
        
        List<UserEntity> admins = userRepository.findByRole(Role.admin);
        
        long totalReports = reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
        if (totalReports >= 3) {
            String blindMsg = "";
            if (targetType == ReportTargetType.post) {
                postRepository.findById(targetId).ifPresent(p -> { p.blind(); postRepository.save(p); });
                blindMsg = "(시스템: 누적 3회 이상 접수되어 해당 게시글이 자동 블라인드 되었습니다.)";
            } else if (targetType == ReportTargetType.party) {
                partyRepository.findById(targetId).ifPresent(p -> { p.blind(); partyRepository.save(p); });
                blindMsg = "(시스템: 누적 3회 이상 접수되어 해당 파티가 자동 블라인드 되었습니다.)";
            }
            
            for (UserEntity admin : admins) {
                notificationRepository.save(new NotificationEntity(admin, "SYSTEM", "자동 블라인드 조치", "'" + targetLabel + "' 관련 " + blindMsg, linkUrl));
            }
        }

        for (UserEntity admin : admins) {
            notificationRepository.save(new NotificationEntity(admin, "REPORT", "새로운 신고 접수", "'" + targetLabel + "'에 대한 신고가 접수되었습니다.", linkUrl));
        }
    }

    /**
     * id 로 받아서 이 메서드 안에서 직접 조회한다(컨트롤러에서 미리 조회해 넘기지 않는다) -
     * 그래야 이 트랜잭션이 엔티티를 제대로 관리해서 resolve()/dismiss() 로 바뀐 상태 값이
     * 실제로 DB 에 반영된다(PostService.toggleLike 에서 겪었던 detached 엔티티 버그와 동일한 패턴 회피).
     */
    @Transactional
    public void resolve(Long reportId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        report.resolve();
    }

    @Transactional
    public void dismiss(Long reportId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        report.dismiss();
    }

    /**
     * [v16 신규] 관리자가 신고를 승인 처리하며 콘텐츠 최종조치(비공개/삭제)까지 함께 실행한다.
     * 처리 완료 시, 이 신고의 "실제 책임자"(직접 신고면 본인, 게시글/파티 신고면 작성자/방장)를
     * 판정해서 그 사람을 대상으로 한 누적 처리 신고 건수가 3의 배수에 새로 도달했으면
     * MannerTempService 에 위임해 -1.0 을 반복 적용한다(필드제약조건 확정 사항).
     */
    @Transactional
    public void resolveWithAction(Long reportId, ReportActionTaken action, UserEntity admin) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        applyContentAction(report, action);
        report.resolveWithAction(admin, action);

        UserEntity offendingUser = resolveOffendingUser(report);
        if (offendingUser != null) {
            long cumulative = countResolvedReportsAgainst(offendingUser);
            mannerTempService.applyReportPenaltyIfThresholdReached(offendingUser, cumulative, report.getId());
        }
    }

    /** 신고된 콘텐츠에 실제 조치(비공개/삭제)를 반영한다. user 신고는 조치할 콘텐츠가 없어 건너뛴다. */
    private void applyContentAction(ReportEntity report, ReportActionTaken action) {
        if (action == null || action == ReportActionTaken.none) return;
        if (report.getTargetType() == ReportTargetType.post) {
            postRepository.findById(report.getTargetId()).ifPresent(p -> { p.blind(); postRepository.save(p); });
        } else if (report.getTargetType() == ReportTargetType.party) {
            partyRepository.findById(report.getTargetId()).ifPresent(p -> { p.blind(); partyRepository.save(p); });
        }
        // action == deleted 인 경우도 완전 삭제 대신 블라인드로 처리한다 - 게시글/파티는
        // 댓글·좋아요·멤버·계획표 등 연쇄 참조가 많아 안전하게 숨기는 쪽을 기본값으로 둔다.
    }

    /** target_type 이 다형이라 SQL 조인이 안 되므로, 애플리케이션 레벨에서 "누구 책임인지"를 판정한다. */
    private UserEntity resolveOffendingUser(ReportEntity report) {
        return switch (report.getTargetType()) {
            case user -> userRepository.findById(report.getTargetId()).orElse(null);
            case post -> postRepository.findById(report.getTargetId()).map(PostEntity::getUser).orElse(null);
            case party -> partyRepository.findById(report.getTargetId()).map(PartyEntity::getOwner).orElse(null);
        };
    }

    /**
     * 이 사용자를 대상으로(직접신고 또는 그가 작성/소유한 콘텐츠 신고 포함) 지금까지
     * 처리 완료(resolved)된 신고가 총 몇 건인지 센다. 다형 연관이라 조인이 안 되므로
     * 처리 완료된 신고 전체를 훑어 애플리케이션에서 판정한다(이 프로젝트 규모에서는 충분히 합리적).
     */
    private long countResolvedReportsAgainst(UserEntity offendingUser) {
        return reportRepository.findByStatus(ReportStatus.resolved).stream()
                .filter(r -> offendingUser.getId().equals(
                        resolveOffendingUser(r) == null ? null : resolveOffendingUser(r).getId()))
                .count();
    }
}
