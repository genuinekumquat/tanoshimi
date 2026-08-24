package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Optional<Attachment> findByFilePath(String filePath);
    List<Attachment> findByStatus(Attachment.FileStatus status);
    
    // 파일 중복 검사용 (업로드 전 해시 체크)
    Optional<Attachment> findFirstByFileHash(String fileHash);
}
