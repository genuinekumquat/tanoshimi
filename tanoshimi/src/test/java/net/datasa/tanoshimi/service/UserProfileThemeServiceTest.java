package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserProfileThemeEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserProfileThemeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** [FR-MYP-08] 프로필 배경 꾸미기 - 허용 목록 밖의 theme_key 가 저장·노출되지 않는지 검증. */
@ExtendWith(MockitoExtension.class)
class UserProfileThemeServiceTest {

    @Mock private UserProfileThemeRepository userProfileThemeRepository;

    @InjectMocks
    private UserProfileThemeService userProfileThemeService;

    @Test
    void currentTheme_저장된_값이_없으면_기본_테마() {
        UserEntity me = mock(UserEntity.class);
        when(userProfileThemeRepository.findByUser(me)).thenReturn(Optional.empty());

        assertThat(userProfileThemeService.currentTheme(me)).isEqualTo(UserProfileThemeService.DEFAULT_THEME);
    }

    @Test
    void currentTheme_목록에_없는_값이_저장돼_있으면_기본_테마() {
        UserEntity me = mock(UserEntity.class);
        when(userProfileThemeRepository.findByUser(me))
                .thenReturn(Optional.of(new UserProfileThemeEntity(me, "<script>")));

        assertThat(userProfileThemeService.currentTheme(me)).isEqualTo(UserProfileThemeService.DEFAULT_THEME);
    }

    @Test
    void changeTheme_처음이면_새_행을_저장() {
        UserEntity me = mock(UserEntity.class);
        when(userProfileThemeRepository.findByUser(me)).thenReturn(Optional.empty());

        userProfileThemeService.changeTheme(me, "sakura_pink");

        ArgumentCaptor<UserProfileThemeEntity> captor = ArgumentCaptor.forClass(UserProfileThemeEntity.class);
        verify(userProfileThemeRepository).save(captor.capture());
        assertThat(captor.getValue().getThemeKey()).isEqualTo("sakura_pink");
    }

    @Test
    void changeTheme_이미_있으면_그_행을_갱신() {
        UserEntity me = mock(UserEntity.class);
        UserProfileThemeEntity existing = new UserProfileThemeEntity(me, "sea_blue");
        when(userProfileThemeRepository.findByUser(me)).thenReturn(Optional.of(existing));

        userProfileThemeService.changeTheme(me, "night_sky");

        assertThat(existing.getThemeKey()).isEqualTo("night_sky");
        verify(userProfileThemeRepository, never()).save(any());
    }

    @Test
    void changeTheme_목록에_없는_키면_INVALID_INPUT() {
        UserEntity me = mock(UserEntity.class);

        assertThatThrownBy(() -> userProfileThemeService.changeTheme(me, "rainbow"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(userProfileThemeRepository);
    }

    @Test
    void changeTheme_null_이면_INVALID_INPUT() {
        UserEntity me = mock(UserEntity.class);

        assertThatThrownBy(() -> userProfileThemeService.changeTheme(me, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(userProfileThemeRepository);
    }
}
