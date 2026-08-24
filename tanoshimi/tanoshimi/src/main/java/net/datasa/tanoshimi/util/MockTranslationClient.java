package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.domain.entity.PreferredLang;
import org.springframework.stereotype.Component;

/** 개발용 더미 번역기 - 실제 번역 대신 태그만 붙여서 "번역이 호출됐다"는 것을 화면에서 확인할 수 있게 한다. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.translation.provider", havingValue = "mock", matchIfMissing = true)
public class MockTranslationClient implements TranslationClient {

    @Override
    public String translate(String text, PreferredLang from, PreferredLang to) {
        if (from == to) return text;
        String tag = to == PreferredLang.ja ? "[JA] " : "[KO] ";
        return tag + text;
    }
}
