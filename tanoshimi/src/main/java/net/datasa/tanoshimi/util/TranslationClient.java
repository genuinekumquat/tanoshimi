package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.domain.entity.PreferredLang;

/**
 * 번역 API 인터페이스
 */
public interface TranslationClient {
    String translate(String text, PreferredLang from, PreferredLang to);
}
