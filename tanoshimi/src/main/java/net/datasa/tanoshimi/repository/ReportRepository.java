package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ReportEntity;
import net.datasa.tanoshimi.domain.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<ReportEntity, Long> {
    /** 관리자 화면에서 report.reporter.name 을 바로 찍어 쓰므로 미리 JOIN FETCH 한다. */
    @EntityGraph(attributePaths = {"reporter"})
    Page<ReportEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);
    long countByTargetTypeAndTargetId(net.datasa.tanoshimi.domain.entity.ReportTargetType type, Long id);

    /**
     * [v16 신규] 신고 3회 누적 판정을 위해, 처리 완료(resolved)된 신고 전체를 훑어서
     * "이 신고 대상의 실제 책임자(유저 직접신고면 본인, 게시글/파티 신고면 작성자/방장)"가
     * 누구인지 ReportService 가 판정한다. target_type이 다형이라 SQL 조인이 안 되므로
     * 애플리케이션 레벨에서 계산 - 이 프로젝트 규모(소량 데이터)에서는 충분히 합리적인 방식이다.
     */
    java.util.List<ReportEntity> findByStatus(ReportStatus status);
}
