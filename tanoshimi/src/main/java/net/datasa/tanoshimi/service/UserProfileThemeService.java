package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserProfileThemeEntity;
import net.datasa.tanoshimi.repository.UserProfileThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** [v16 신규] 마이페이지 프로필 배경/스킨 꾸미기. 담당: 김민규(⑥). */
@Service
@RequiredArgsConstructor
public class UserProfileThemeService {

    private static final String DEFAULT_THEME = "forest_green";

    private final UserProfileThemeRepository userProfileThemeRepository;

    @Transactional(readOnly = true)
    public String currentTheme(UserEntity user) {
        return userProfileThemeRepository.findByUser(user)
                .map(UserProfileThemeEntity::getThemeKey)
                .orElse(DEFAULT_THEME);
    }

    @Transactional
    public void changeTheme(UserEntity user, String themeKey) {
        userProfileThemeRepository.findByUser(user)
                .ifPresentOrElse(
                        existing -> existing.changeTheme(themeKey),
                        () -> userProfileThemeRepository.save(new UserProfileThemeEntity(user, themeKey))
                );
    }
}
