package net.datasa.tanoshimi.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
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

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.lang.GeoLocation;

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
    public String saveProfileImage(MultipartFile file) {
        return saveImage(file);
    }

    @Override
    @Transactional
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 없습니다.");

        try {
            byte[] fileBytes = file.getBytes();
            
            // 1. 보안 체크 (Spring MultipartFile 기반 MIME 체크 - Tika 의존성 문제 회피)
            String mimeType = file.getContentType();
            if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일만 업로드 가능합니다. (현재: " + mimeType + ")");
            }

            // 2. 파일 중복 검사 (SHA-256 Deduplication)
            String fileHash = calculateHash(fileBytes);
            Optional<Attachment> existingFile = attachmentRepository.findFirstByFileHash(fileHash);
            if (existingFile.isPresent()) {
                log.info("중복 이미지 업로드 감지: {}", fileHash);
                return existingFile.get().getFilePath();
            }

            // 3. EXIF 타겟 (위치/회전) 분석
            Double[] gps = extractGpsAndStripExif(fileBytes);
            int orientation = extractOrientation(fileBytes);

            // 4. 차세대 포맷 WebP 강제 변환 및 저장 준비
            Path uploadDir = Paths.get(uploadDirPath);
            Files.createDirectories(uploadDir);
            String baseFilename = UUID.randomUUID().toString();
            String defaultFilename = baseFilename + ".webp"; 
            Path target = uploadDir.resolve(defaultFilename);

            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                BufferedImage original = ImageIO.read(is);
                if (original != null) {
                    original = correctThumbnailOrientation(original, orientation); 
                    
                    createResizedImageWithWatermark(original, uploadDir.resolve(baseFilename + "_150.webp").toFile(), 150);
                    createResizedImageWithWatermark(original, uploadDir.resolve(baseFilename + "_400.webp").toFile(), 400);
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
                .extension("webp")
                .fileSize((long) fileBytes.length)
                .mimeType("image/webp")
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
    public void markActive(String fileUrl) {
        if (fileUrl == null) return;
        attachmentRepository.findByFilePath(fileUrl).ifPresent(attachment -> {
            attachment.markAsActive();
            attachmentRepository.save(attachment);
        });
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
        return null; // 생략..
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
        return "여름비치,수영복,여행지"; // Dummy for local
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

    private Double[] extractGpsAndStripExif(byte[] fileBytes) {
        Double[] coords = new Double[]{null, null};
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(is);
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null && gpsDir.getGeoLocation() != null) {
                GeoLocation location = gpsDir.getGeoLocation();
                coords[0] = location.getLatitude();
                coords[1] = location.getLongitude();
            }
        } catch (Exception e) {
            log.warn("EXIF GPS 추출 불가");
        }
        return coords;
    }

    private int extractOrientation(byte[] fileBytes) {
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(is);
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {}
        return 1; // Default
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

        ImageIO.write(resized, "webp", targetFile);
    }

    private void addWatermark(Graphics2D g, String watermarkText, int width, int height) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)); 
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, Math.max(12, width / 20)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(watermarkText, width - fm.stringWidth(watermarkText) - 10, height - fm.getHeight() + 10);
    }
}
