package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.SupportEntity;
import net.datasa.tanoshimi.repository.SupportCommentRepository;
import net.datasa.tanoshimi.repository.SupportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock private SupportRepository supportRepository;
    @Mock private SupportCommentRepository supportCommentRepository;

    @InjectMocks
    private SupportService supportService;

    private SupportEntity post(String guestId, String guestPassword) {
        return SupportEntity.builder()
                .guestId(guestId).guestPassword(guestPassword)
                .title("t").content("c")
                .build();
    }

    @Test
    void verifyGuest_아이디와_비번이_모두_일치하면_true() {
        when(supportRepository.findById(1L)).thenReturn(Optional.of(post("guest", "pw")));
        assertThat(supportService.verifyGuest(1L, "guest", "pw")).isTrue();
    }

    @Test
    void verifyGuest_비번이_틀리면_false() {
        when(supportRepository.findById(1L)).thenReturn(Optional.of(post("guest", "pw")));
        assertThat(supportService.verifyGuest(1L, "guest", "wrong")).isFalse();
    }

    @Test
    void verifyGuest_글이_없으면_false() {
        when(supportRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(supportService.verifyGuest(9L, "guest", "pw")).isFalse();
    }
}
