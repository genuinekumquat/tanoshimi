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

/** 신고 접수 + 관리자 처리(승인/기각). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    @Transactional
    public void submit(UserEntity reporter, ReportTargetType targetType, Long targetId, String targetLabel, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 사유를 입력해 주세요.");
        }
        reportRepository.save(new ReportEntity(reporter, targetType, targetId, targetLabel, reason.trim()));
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
