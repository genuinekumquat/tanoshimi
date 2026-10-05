package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.TitleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserTitleEntity;
import net.datasa.tanoshimi.repository.UserTitleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** 파티원 목록 등에 붙는 대표 칭호 배지 - representativeTitles 의 선택 규칙. */
@ExtendWith(MockitoExtension.class)
class TitleServiceRepresentativeTest {

    @Mock private UserTitleRepository userTitleRepository;
    @InjectMocks private TitleService titleService;

    @Test
    void 장착한_칭호가_있으면_더_최근에_딴_칭호보다_우선한다() {
        UserEntity user = user(1L);
        TitleEntity equipped = mock(TitleEntity.class);
        TitleEntity newer = mock(TitleEntity.class);
        when(userTitleRepository.findByUserIn(anyCollection())).thenReturn(List.of(
                owned(user, equipped, LocalDateTime.of(2026, 1, 1, 0, 0), true),
                owned(user, newer, LocalDateTime.of(2026, 9, 1, 0, 0), false)));

        Map<Long, TitleEntity> result = titleService.representativeTitles(List.of(user));

        assertThat(result).containsEntry(1L, equipped);
    }

    @Test
    void 장착한_칭호가_없으면_가장_최근에_딴_칭호를_고른다() {
        UserEntity user = user(1L);
        TitleEntity older = mock(TitleEntity.class);
        TitleEntity newest = mock(TitleEntity.class);
        when(userTitleRepository.findByUserIn(anyCollection())).thenReturn(List.of(
                owned(user, newest, LocalDateTime.of(2026, 9, 1, 0, 0), false),
                owned(user, older, LocalDateTime.of(2026, 1, 1, 0, 0), false)));

        assertThat(titleService.representativeTitles(List.of(user))).containsEntry(1L, newest);
    }

    @Test
    void 칭호가_없는_사람은_결과에_없고_빈_목록이면_조회하지_않는다() {
        UserEntity withTitle = user(1L);
        UserEntity withoutTitle = user(2L);
        TitleEntity title = mock(TitleEntity.class);
        when(userTitleRepository.findByUserIn(anyCollection())).thenReturn(List.of(
                owned(withTitle, title, LocalDateTime.of(2026, 1, 1, 0, 0), false)));

        Map<Long, TitleEntity> result = titleService.representativeTitles(List.of(withTitle, withoutTitle));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(titleService.representativeTitles(List.of())).isEmpty();
        verify(userTitleRepository, times(1)).findByUserIn(anyCollection());
    }

    private static UserEntity user(Long id) {
        UserEntity user = mock(UserEntity.class);
        lenient().when(user.getId()).thenReturn(id);
        return user;
    }

    private static UserTitleEntity owned(UserEntity user, TitleEntity title, LocalDateTime earnedAt, boolean equipped) {
        UserTitleEntity ut = new UserTitleEntity(user, title);
        ReflectionTestUtils.setField(ut, "earnedAt", earnedAt);
        if (equipped) {
            ut.equip();
        }
        return ut;
    }
}
