package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.Attachment;
import net.datasa.tanoshimi.repository.AttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 사진 다운로드 - 업로드 때 webp 로 바꿔 저장한 사진을, 올린 사람의 원래 형식(jpg/png)으로 되돌려 내려준다.
 * 원본 파일은 저장하지 않으므로(EXIF 위치정보 제거 + 용량, LocalStorageServiceImpl 참고)
 * 형식만 원래대로 돌아가고 크기는 저장본(최대 가로 800px) 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoDownloadService {
	
	private static final String UPLOAD_PREFIX = "/uploads/";
	/** 저장 파일명은 UUID.webp, demo_xxx.jpg 같은 형태뿐 - 경로 문자(/ \ ..)가 섞인 요청은 거른다. */
	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*");
	
	private final AttachmentRepository attachmentRepository;
	
	@Value("${app.upload-dir}")
	private String uploadDirPath;
	
	public record DownloadFile(byte[] bytes, String contentType, String filename) {}
	
	public Optional<DownloadFile> prepare(String fileUrl) {
		if (fileUrl == null || !fileUrl.startsWith(UPLOAD_PREFIX)) return Optional.empty();
		String saveName = fileUrl.substring(UPLOAD_PREFIX.length());
		if (!SAFE_NAME.matcher(saveName).matches()) return Optional.empty();
		
		// 업로드 폴더 밖의 파일은 절대 읽지 않는다 (경로 조작 방지 이중 체크)
		Path dir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
		Path file = dir.resolve(saveName).normalize();
		if (!file.startsWith(dir) || !Files.isRegularFile(file)) return Optional.empty();
		
		String originalName = attachmentRepository.findByFilePath(fileUrl)
				.map(Attachment::getOriginalFilename)
				.orElse(null);
		String storedFormat = normalize(extensionOf(saveName));
		String targetFormat = targetFormat(normalize(extensionOf(originalName)), storedFormat);
		
		byte[] raw;
		try {
			raw = Files.readAllBytes(file);
		} catch (IOException e) {
			log.warn("사진 읽기 실패: {}", fileUrl, e);
			return Optional.empty();
		}
		
		if (targetFormat.equals(storedFormat)) {
			return Optional.of(new DownloadFile(raw, contentTypeOf(storedFormat), downloadName(originalName, storedFormat)));
		}
		try {
			byte[] converted = convert(raw, targetFormat);
			return Optional.of(new DownloadFile(converted, contentTypeOf(targetFormat), downloadName(originalName, targetFormat)));
		} catch (IOException e) {
			// 변환이 실패해도 다운로드 자체는 되게 - 저장본(webp)을 그대로 준다
			log.warn("사진 형식 변환 실패, 저장본 그대로 내려줌: {}", fileUrl, e);
			return Optional.of(new DownloadFile(raw, contentTypeOf(storedFormat), downloadName(originalName, storedFormat)));
		}
	}
	
	/** 원래 형식을 보고 받을 형식을 정한다. 원래 형식을 모르면 webp 만 jpg 로 바꾼다. */
	private String targetFormat(String original, String stored) {
		return switch (original) {
			case "jpg" -> "jpg";
			case "png", "gif" -> "png";   // gif 는 webp 저장 때 이미 정지 이미지가 됐으므로 png 로
			case "webp" -> "webp";
			default -> "webp".equals(stored) ? "jpg" : stored;
		};
	}
	
	private byte[] convert(byte[] raw, String format) throws IOException {
		BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
		if (src == null) throw new IOException("이미지를 읽을 수 없습니다.");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if ("jpg".equals(format)) {
			writeJpeg(toRgb(src), out);
		} else if (!ImageIO.write(src, format, out)) {
			throw new IOException("지원하지 않는 형식: " + format);
		}
		return out.toByteArray();
	}
	
	/** jpg 는 투명도를 못 담으므로 흰 배경 위에 다시 그린다. */
	private BufferedImage toRgb(BufferedImage src) {
		if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
		BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, src.getWidth(), src.getHeight());
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return rgb;
	}
	
	/** ImageIO.write(img, "jpg") 기본 화질(0.75)은 뭉개져서 0.92 로 저장한다. */
	private void writeJpeg(BufferedImage img, ByteArrayOutputStream out) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(0.92f);
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(img, null, null), param);
		} finally {
			writer.dispose();
		}
	}
	
	/** 원래 파일명(확장자 제외) + 새 확장자. 파일명에 쓸 수 없는 문자는 뺀다. */
	private String downloadName(String originalName, String format) {
		String base = originalName == null ? "" : originalName;
		int dot = base.lastIndexOf('.');
		if (dot >= 0) base = base.substring(0, dot);
		base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
		if (base.isEmpty()) base = "tanoshimi-photo";
		if (base.length() > 80) base = base.substring(0, 80);
		return format.isEmpty() ? base : base + "." + format;
	}
	
	private String contentTypeOf(String format) {
		return switch (format) {
			case "jpg" -> "image/jpeg";
			case "png" -> "image/png";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			default -> "application/octet-stream";
		};
	}
	
	private String extensionOf(String name) {
		if (name == null) return null;
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1);
	}
	
	/** null → "", 대문자 → 소문자, jpeg → jpg 로 맞춘다. */
	private String normalize(String ext) {
		if (ext == null) return "";
		String e = ext.toLowerCase(Locale.ROOT);
		return "jpeg".equals(e) ? "jpg" : e;
	}
}