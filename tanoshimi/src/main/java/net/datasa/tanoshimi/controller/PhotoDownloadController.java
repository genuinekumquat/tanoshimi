package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.service.PhotoDownloadService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** 사진 다운로드 - 저장본(webp)을 올린 사람의 원래 형식으로 바꿔서 첨부파일로 내려준다. */
@RestController
@RequiredArgsConstructor
public class PhotoDownloadController {
	
	private final PhotoDownloadService photoDownloadService;
	
	@GetMapping("/api/files/download")
	public ResponseEntity<byte[]> download(@RequestParam String url) {
		return photoDownloadService.prepare(url)
				.map(f -> ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
								.filename(f.filename(), StandardCharsets.UTF_8)
								.build().toString())
						.contentType(MediaType.parseMediaType(f.contentType()))
						.body(f.bytes()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}