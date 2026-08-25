package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.core.SdkBytes;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageServiceImpl implements FileStorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:tanoshimi-bucket}")
    private String bucketName;
    
    @Value("${aws.s3.domain:}")
    private String s3Domain;

    @Override
    public String saveProfileImage(MultipartFile file) {
        return uploadToS3(file, "profile");
    }

    @Override
    public String saveImage(MultipartFile file) {
        return uploadToS3(file, "images");
    }

    @Override
    public List<String> saveImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return new ArrayList<>();
        return files.stream().map(this::saveImage).collect(Collectors.toList());
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null) return;
        try {
            String key = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패", e);
        }
    }

    @Override
    public String saveDocument(MultipartFile file) {
        return uploadToS3(file, "documents");
    }

    @Override
    @Async
    public CompletableFuture<String> saveImageAsync(MultipartFile file) {
        return CompletableFuture.completedFuture(saveImage(file));
    }

    @Override
    public String generateDirectUploadUrl(String extension) {
        // s3-presigner 의존성 문제 회피를 위해 주석 처리됨.
        // AWS SDK v2의 S3 모듈에 포함되어 있지 않은 경우 명시적 의존성 선언이 필요하나
        // Gradle 종속성 충돌 문제로 현재 로직에서 제거
        throw new UnsupportedOperationException("현재 환경에서 Pre-signed URL을 지원하지 않습니다.");
    }

    @Override
    public String analyzeImage(MultipartFile file) {
        return "AI 분석 사용 불가 (의존성 제한)";
    }

    private String uploadToS3(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 없습니다.");
        try {
            String filename = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
            s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(filename).contentType(file.getContentType()).build(), 
                               RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            String baseUrl = (s3Domain != null && !s3Domain.isEmpty()) ? s3Domain : "https://" + bucketName + ".s3.amazonaws.com";
            return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + filename;
        } catch (IOException e) {
            log.error("S3 업로드 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 업로드에 실패했습니다.");
        }
    }
}
