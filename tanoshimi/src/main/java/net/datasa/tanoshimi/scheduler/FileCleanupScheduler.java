package net.datasa.tanoshimi.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.Attachment;
import net.datasa.tanoshimi.repository.AttachmentRepository;
import net.datasa.tanoshimi.service.FileStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고아 파일(어디에도 쓰이지 않는 파일)을 주기적으로 정리하는 스케줄러
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class FileCleanupScheduler {

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;

    /**
     * 매일 새벽 4시에 실행 (크론 표현식: 초 분 시 일 월 요일)
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOrphanedFiles() {
        log.info("[스케줄러] 고아 파일 정리 작업 시작");

        // 상태가 ORPHANED이고, 며칠 이상 경과된 파일을 대량으로 조회
        // 여기서는 예시로 ORPHANED 전체 조회 (실제 운영 시에는 uploadDate가 n일 지난 파일 조건 추가 권장)
        List<Attachment> orphanedFiles = attachmentRepository.findByStatus(Attachment.FileStatus.ORPHANED);

        int count = 0;
        for (Attachment attachment : orphanedFiles) {
            try {
                // 실제 스토리지 연동(로컬 or S3)에 따른 삭제 처리
                fileStorageService.deleteFile(attachment.getFilePath()); 
                count++;
            } catch (Exception e) {
                log.error("파일 삭제 중 오류 발생: {}", attachment.getFilePath(), e);
            }
        }

        log.info("[스케줄러] 고아 파일 정리 작업 완료. 총 {}개의 파일 삭제됨.", count);
    }
}
