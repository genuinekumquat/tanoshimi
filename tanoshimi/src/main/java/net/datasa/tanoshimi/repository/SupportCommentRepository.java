package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.SupportCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportCommentRepository extends JpaRepository<SupportCommentEntity, Long> {
    List<SupportCommentEntity> findBySupportIdAndParentCommentIsNullOrderByCreatedAtAsc(Long supportId);
}
