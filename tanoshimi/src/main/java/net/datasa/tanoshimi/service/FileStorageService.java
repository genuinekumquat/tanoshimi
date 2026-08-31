package net.datasa.tanoshimi.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 전천후 파일 스토리지 서비스 인터페이스
 */
public interface FileStorageService {
    
    // ======== [기본 업로드 로직] ========
    
    /** 과거 호환용 프로필 이미지 저장 */
    String saveProfileImage(MultipartFile file);

    /** 단일 이미지 저장 (MIME Type 체크, 워터마크, WebP, EXIF GPS 추출 적용) */
    String saveImage(MultipartFile file);

    /** 다중 파일 업로드 지원 */
    List<String> saveImages(List<MultipartFile> files);

    /** 삭제 */
    void deleteFile(String fileUrl);

    /**
     * 업로드 직후 ORPHANED 상태로 생성된 파일을, 실제로 어떤 엔티티(프로필/게시글/파티 등)에
     * 연결됐을 때 ACTIVE 로 전환한다. 이걸 호출하지 않으면 FileCleanupScheduler 가 다음날
     * 새벽에 "고아 파일"로 오인해 삭제해버린다. (S3 구현은 orphan 추적을 하지 않으므로 no-op)
     */
    default void markActive(String fileUrl) {}

    /** 일반 파일/문서 저장 */
    String saveDocument(MultipartFile file);
    
    /** 비동기 리사이징 (넌블로킹) */
    CompletableFuture<String> saveImageAsync(MultipartFile file);

    // ======== [고급 기능 추가] ========

    /** 
     * 클라이언트가 S3로 직접 업로드하도록 티켓(Pre-signed URL) 발급 (네트워크 부하 분산) 
     * @param extension 파일 명 확장자 (예: "jpg")
     * @return 발급된 URL
     */
    String generateDirectUploadUrl(String extension);
    
    /**
     * AI 기반 이미지 내용 판독 (유해물 차단 통과 후, "바다, 스시" 같은 태그 문자열 반환)
     */
    String analyzeImage(MultipartFile file);
}
