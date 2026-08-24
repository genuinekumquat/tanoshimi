package net.datasa.tanoshimi.config;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    /**
     * 한국어/일본어 다국어 지원.
     * ?lang=ko 또는 ?lang=ja 파라미터를 아무 페이지에나 붙이면 언어가 바뀐다.
     * 메인 화면 우측 상단의 작은 KO/JA 토글이 이 파라미터를 붙여서 요청을 보낸다(fragments/layout.html 참고).
     * 쿠키에 저장되므로 새로고침해도 유지된다.
     *
     * messageSource 를 직접 빈으로 등록해 basename/encoding/fallback 을 명시적으로 고정한다
     * (application.yml 의 spring.messages.* 설정과 내용은 같지만, 로케일별 파일 중 하나라도
     * 못 찾았을 때 예외 대신 조용히 넘어가지 않도록 여기서 확실히 잡아둔다).
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false); // 설정한 언어만 사용하도록 강제
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("TANOSHIMI_LANG");
        resolver.setDefaultLocale(Locale.KOREAN);
        resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
