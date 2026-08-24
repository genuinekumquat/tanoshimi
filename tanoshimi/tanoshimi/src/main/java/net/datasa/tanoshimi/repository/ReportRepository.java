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
}
