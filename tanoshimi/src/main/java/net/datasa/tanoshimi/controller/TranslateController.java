package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.TranslateRequest;
import net.datasa.tanoshimi.domain.entity.PreferredLang;
import net.datasa.tanoshimi.util.TranslationClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅 메시지 번역 버튼 전용 API.
 * 결과를 저장하지 않는다 - 누를 때마다 그 자리에서만 텍스트가 바뀌고, 새로고침하면 원문으로 돌아온다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslationClient translationClient;

    @PostMapping("/translate")
    public ApiResponse<String> translate(@RequestBody TranslateRequest req) {
        String text = req.text();
        if (text == null || text.isBlank()) {
            return ApiResponse.ok(text);
        }

        // 사용자의 선호 언어(MY_LANG)와 상관없이 클릭한 메시지의 텍스트를 감지하여 반대 언어로 번역
        // (한국어 메시지를 누르면 일본어로, 일본어 메시지를 누르면 한국어로)
        boolean containsKorean = text.matches(".*[가-힣]+.*");
        
        PreferredLang source = containsKorean ? PreferredLang.ko : PreferredLang.ja;
        PreferredLang target = containsKorean ? PreferredLang.ja : PreferredLang.ko;

        String translated = translationClient.translate(req.text(), source, target);
        return ApiResponse.ok(translated);
    }
}
