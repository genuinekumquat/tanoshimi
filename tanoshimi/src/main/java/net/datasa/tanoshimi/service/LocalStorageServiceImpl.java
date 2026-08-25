package net.datasa.tanoshimi.service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.domain.entity.Attachment;
import net.datasa.tanoshimi.repository.AttachmentRepository;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");
    // MIME type check using Spring's MultipartFile content type instead of Tika to avoid NoClassDefFoundError
    private static final List<String> ALLOWED_MIME_TYPES = List.of("image/jpeg", "image/png", "image/gif", "image/webp");
    
    private final AttachmentRepository attachmentRepository;
    
    @Value("${app.upload-dir}")
    private String uploadDirPath;

    public LocalStorageServiceImpl(AttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    @Override
    @Transactional
    public String saveProfileImage(MultipartFile file) {
        return saveImage(file);
    }

    @Override
    @Transactional
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 비어있습니다.");
        }

        try {
            // 1. MIME / Ext 검사
            String ext = Optional.ofNullable(file.getOriginalFilename())
                    .filter(f -> f.contains("."))
                    .map(f -> f.substring(f.lastIndexOf(".") + 1).toLowerCase())
                    .orElse("");

            if (!ALLOWED_EXTENSIONS.contains(ext) || !ALLOWED_MIME_TYPES.contains(file.getContentType())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 이미지 원본입니다.");
            }

            // 2. 파일 중복 검사 
            byte[] fileBytes = file.getBytes();
            String fileHash = calculateHash(fileBytes);
            Optional<Attachment> existingFile = attachmentRepository.findFirstByFileHash(fileHash);
            if (existingFile.isPresent()) {
                log.info("중복 이미지 무시 및 캐시 반환: {}", fileHash);
                return existingFile.get().getFilePath();
            }

            // EXIF 의존성 에러 방지를 위해 메타데이터 추출 기능 무효화, 
            // Java 내장 라이브러리를 통해 EXIF 정보는 복사하지 않음으로써 Stripping 처리
            Double[] gps = new Double[]{null, null};
            int orientation = 1;

            // 4. 차세대 포맷 WebP 대신 JPG 라이브러리로 fallback(동기화 에러 방지)
            Path uploadDir = Paths.get(uploadDirPath);
            Files.createDirectories(uploadDir);
            String baseFilename = UUID.randomUUID().toString();
            String defaultFilename = baseFilename + ".jpg"; 
            Path target = uploadDir.resolve(defaultFilename);

            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                BufferedImage original = ImageIO.read(is);
                if (original != null) {
                    original = correctThumbnailOrientation(original, orientation); 
                    
                    createResizedImageWithWatermark(original, uploadDir.resolve(baseFilename + "_150.jpg").toFile(), 150);
                    createResizedImageWithWatermark(original, uploadDir.resolve(baseFilename + "_400.jpg").toFile(), 400);
                    createResizedImageWithWatermark(original, target.toFile(), 800); 
                } else {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 이미지 파일입니다.");
                }
            }

            String fileUrl = "/uploads/" + defaultFilename;

            // 5. DB 등록
            Attachment attachment = Attachment.builder()
                .originalFilename(file.getOriginalFilename())
                .saveFilename(defaultFilename)
                .filePath(fileUrl)
                .extension("jpg")
                .fileSize((long) fileBytes.length)
                .mimeType("image/jpeg")
                .fileHash(fileHash)
                .latitude(gps[0])
                .longitude(gps[1]) 
                .status(Attachment.FileStatus.ORPHANED)
                .build();
            attachmentRepository.save(attachment);

            return fileUrl;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("파일 저장 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 최적화 중 오류가 발생했습니다.");
        }
    }

    @Override
    @Transactional
    public List<String> saveImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return new ArrayList<>();
        return files.stream().map(this::saveImage).toList();
    }

    @Override
    @Transactional
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) return;
        String filename = fileUrl.replace("/uploads/", "");
        try {
            Files.deleteIfExists(Paths.get(uploadDirPath).resolve(filename));
            attachmentRepository.findByFilePath(fileUrl).ifPresent(attachmentRepository::delete);
        } catch (IOException e) {
            log.error("파일 삭제 오류: {}", fileUrl, e);
        }
    }

    @Override
    @Transactional
    public String saveDocument(MultipartFile file) {
        return null; // 생략
    }

    @Override
    @Async
    public CompletableFuture<String> saveImageAsync(MultipartFile file) {
        return CompletableFuture.completedFuture(saveImage(file));
    }

    @Override
    public String generateDirectUploadUrl(String extension) {
        throw new UnsupportedOperationException("로컬 스토리지에서는 Pre-signed URL을 지원하지 않습니다. (S3 전용 기능)");
    }

    @Override
    public String analyzeImage(MultipartFile file) {
        return "여름비치,해양여행지"; // Dummy for local
    }

    private String calculateHash(byte[] fileBytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(fileBytes);
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private BufferedImage correctThumbnailOrientation(BufferedImage img, int orientation) {
        if (orientation == 1) return img; // 정상
        AffineTransform t = new AffineTransform();
        int width = img.getWidth(), height = img.getHeight();
        
        switch (orientation) {
            case 3: t.translate(width, height); t.rotate(Math.PI); break;
            case 6: t.translate(height, 0); t.rotate(Math.PI / 2); break;
            case 8: t.translate(0, width); t.rotate(-Math.PI / 2); break;
        }

        if (orientation == 6 || orientation == 8) {
            BufferedImage rot = new BufferedImage(height, width, img.getType());
            Graphics2D g = rot.createGraphics();
            g.transform(t); g.drawImage(img, 0, 0, null); g.dispose();
            return rot;
        } else if (orientation == 3) {
            BufferedImage rot = new BufferedImage(width, height, img.getType());
            Graphics2D g = rot.createGraphics();
            g.transform(t); g.drawImage(img, 0, 0, null); g.dispose();
            return rot;
        }
        return img;
    }

    private void createResizedImageWithWatermark(BufferedImage original, File targetFile, int maxWidth) throws IOException {
        int oWidth = original.getWidth();
        int oHeight = original.getHeight();
        int tWidth = Math.min(oWidth, maxWidth);
        int tHeight = (int) ((double) tWidth / oWidth * oHeight);

        BufferedImage resized = new BufferedImage(tWidth, tHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, tWidth, tHeight, null);

        addWatermark(g, "Tanoshimi", tWidth, tHeight);
        g.dispose();

        ImageIO.write(resized, "jpg", targetFile);
    }

    private void addWatermark(Graphics2D g, String watermarkText, int width, int height) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)); 
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, Math.max(12, width / 20)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(watermarkText, width - fm.stringWidth(watermarkText) - 10, height - fm.getHeight() + 10);
    }
}