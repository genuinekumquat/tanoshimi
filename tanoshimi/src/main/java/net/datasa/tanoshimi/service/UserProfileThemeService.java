package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserProfileThemeEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserProfileThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [v16 신규] 마이페이지 프로필 배경/스킨 꾸미기. 담당: 김민규(⑥).
 *
 * <p><b>[FR-MYP-08]</b> 서비스만 있고 호출하는 곳이 없던 상태에서 화면을 붙였다.
 * theme_key 는 VARCHAR 라 아무 값이나 들어갈 수 있어서, 고를 수 있는 테마를 {@link #THEMES}
 * 로 고정하고 저장·조회 양쪽에서 이 목록으로 거른다. 실제 색은 app.css 의
 * {@code .profile-theme-<key>} 클래스가 정한다 - 테마를 추가하려면 두 곳을 같이 늘린다.
 */
@Service
@RequiredArgsConstructor
public class UserProfileThemeService {

    public static final String DEFAULT_THEME = "forest_green";

    /** 화면에 보여줄 테마 선택지. 키는 CSS 클래스 이름에 그대로 쓰인다. */
    public record ThemeOption(String key, String label) {}

    public static final List<ThemeOption> THEMES = List.of(
            new ThemeOption(DEFAULT_THEME, "기본"),
            new ThemeOption("sakura_pink", "벚꽃"),
            new ThemeOption("sea_blue", "바다"),
            new ThemeOption("sunset_orange", "노을"),
            new ThemeOption("matcha", "말차"),
            new ThemeOption("night_sky", "밤하늘")
    );

    private final UserProfileThemeRepository userProfileThemeRepository;

    public static boolean isValidTheme(String themeKey) {
        return themeKey != null && THEMES.stream().anyMatch(t -> t.key().equals(themeKey));
    }

    /** 저장된 값이 없거나, 지금은 없어진 테마 키면 기본 테마로 보여준다. */
    @Transactional(readOnly = true)
    public String currentTheme(UserEntity user) {
        return userProfileThemeRepository.findByUser(user)
                .map(UserProfileThemeEntity::getThemeKey)
                .filter(UserProfileThemeService::isValidTheme)
                .orElse(DEFAULT_THEME);
    }

    @Transactional
    public void changeTheme(UserEntity user, String themeKey) {
        if (!isValidTheme(themeKey)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "선택할 수 없는 배경이에요.");
        }
        userProfileThemeRepository.findByUser(user)
                .ifPresentOrElse(
                        existing -> existing.changeTheme(themeKey),
                        () -> userProfileThemeRepository.save(new UserProfileThemeEntity(user, themeKey))
                );
    }
}
