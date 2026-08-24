package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 범용 이미지 업로드 - 게시글, 파티 만들기, 파티 전용 게시판(사진첩) 썸네일에서 공용으로 쓴다.
 * 로그인은 필요하지만(anyRequest().authenticated() 기본 규칙), 별도 소유권 검사는 안 한다 -
 * 파일을 올리는 것 자체는 누가 올리든 위험하지 않고, 실제로 어디에 쓸지는 이 URL 을 받은
 * 클라이언트가 글쓰기 API 호출 시 thumbnailUrl 로 넘기면서 결정된다.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/image")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.saveImage(file);
        return ApiResponse.ok(url);
    }
}
