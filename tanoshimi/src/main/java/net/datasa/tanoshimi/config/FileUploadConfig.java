package net.datasa.tanoshimi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 업로드된 파일(프로필 사진 등)은 프로젝트 폴더 밖의 app.upload-dir(FileStorageService 참고)에
 * 저장한다 - 프로젝트 폴더를 통째로 교체/재압축해도 사진이 안 지워지게 하려는 목적이다.
 * /uploads/** 로 요청이 오면 이 폴더에서 그대로 찾아준다.
 */
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public FileUploadConfig(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Windows 경로(백슬래시)를 포함해서 file: URL 로 정확히 가리키려면 절대경로 + 슬래시 정규화가 필요하다.
        String location = "file:" + uploadDir.replace('\\', '/');
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
