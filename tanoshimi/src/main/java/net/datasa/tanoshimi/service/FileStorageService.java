package net.datasa.tanoshimi.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일 저장 - 프로필 사진, 게시글/파티/모집글 썸네일 등 이미지 전반에 공용으로 쓴다.
 *
 * <p>저장 위치는 프로젝트 폴더 밖(app.upload-dir, 기본값 사용자 홈 디렉토리의 tanoshimi-uploads)이다.
 * 예전엔 실행 위치 기준 ./uploads(프로젝트 폴더 안)를 썼는데, 새 코드를 받아서 프로젝트 폴더를
 * 통째로 교체하거나 재압축할 때마다 업로드된 사진이 같이 사라지는 문제가 있었다.
 * 프로젝트 밖에 고정된 절대경로를 쓰면 코드를 몇 번을 새로 받아도 사진은 안전하게 남는다.
 * (매핑은 FileUploadConfig 참고 - 같은 경로를 읽어서 /uploads/** 로 서빙한다)
 *
 * TODO(다음 단계): 배포 규모가 커지면 로컬 디스크 대신 S3/오브젝트 스토리지로 교체.
 * 지금은 인터페이스 없이 바로 구현했지만, 나중에 교체할 때는 인터페이스로 한 번 감싸는 게 좋다.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDirPath) {
        this.uploadDir = Paths.get(uploadDirPath);
    }

    /** 이름은 과거 호환용으로 남겨두고, 내부적으로는 범용 saveImage 를 그대로 쓴다. */
    public String saveProfileImage(MultipartFile file) {
        return saveImage(file);
    }

    /** 게시글/파티/모집글 썸네일 등 - 이미지면 뭐든 이걸로 저장한다. */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일을 선택해 주세요.");
        }

        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일(jpg, png, gif, webp)만 업로드할 수 있습니다.");
        }

        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + "." + ext;
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + filename;
        } catch (IOException e) {
            log.error("파일 저장 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 업로드에 실패했습니다.");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
