package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String originalFilename;

    @Column(nullable = false, length = 500)
    private String saveFilename;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false, length = 50)
    private String extension;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String mimeType;

    // 🚀 [추가] 중복 파일 방지를 위한 SHA-256 해시값 보관
    @Column(length = 64)
    private String fileHash;

    // 🌍 [추가] 여행 특화: EXIF에서 추출한 GPS 위도/경도 (지도 핀 꽂기용)
    @Column
    private Double latitude;

    @Column
    private Double longitude;

    // 🤖 [추가] AI가 판별한 이미지 태그 (예: "해변, 오사카성, 스시") 검색 필터링용
    @Column(length = 1000)
    private String autoTags;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FileStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime uploadDate;

    public enum FileStatus {
        ACTIVE, ORPHANED
    }

    @Builder
    public Attachment(String originalFilename, String saveFilename, String filePath, 
                      String extension, Long fileSize, String mimeType, String fileHash,
                      Double latitude, Double longitude, String autoTags, FileStatus status) {
        this.originalFilename = originalFilename;
        this.saveFilename = saveFilename;
        this.filePath = filePath;
        this.extension = extension;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.fileHash = fileHash;
        this.latitude = latitude;
        this.longitude = longitude;
        this.autoTags = autoTags;
        this.status = status;
    }

    public void markAsActive() {
        this.status = FileStatus.ACTIVE;
    }

    public void markAsOrphaned() {
        this.status = FileStatus.ORPHANED;
    }
}
