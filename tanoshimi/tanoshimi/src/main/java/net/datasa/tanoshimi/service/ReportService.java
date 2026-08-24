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

/** 신고 접수 + 관리자 처리(승인/기각). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final PartyRepository partyRepository;

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
}
