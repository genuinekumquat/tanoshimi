package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.SupportEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.SupportCommentRepository;
import net.datasa.tanoshimi.repository.SupportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock private SupportRepository supportRepository;
    @Mock private SupportCommentRepository supportCommentRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private SupportService supportService;

    @BeforeEach
    void setUp() {
        supportService = new SupportService(supportRepository, supportCommentRepository, encoder);
    }

    private SupportEntity post(String guestId, String storedPassword) {
        return SupportEntity.builder()
                .guestId(guestId).guestPassword(storedPassword)
                .title("t").content("c")
                .build();
    }

    @Test
    void write_비밀번호는_해시로_저장한다() {
        supportService.write("guest", "pw1234", "t", "c");

        ArgumentCaptor<SupportEntity> captor = ArgumentCaptor.forClass(SupportEntity.class);
        verify(supportRepository).save(captor.capture());
        String stored = captor.getValue().getGuestPassword();
        assertThat(stored).isNotEqualTo("pw1234").startsWith("$2");
        assertThat(encoder.matches("pw1234", stored)).isTrue();
    }

    @Test
    void verifyGuest_아이디와_비번이_모두_일치하면_true() {
        when(supportRepository.findById(1L)).thenReturn(Optional.of(post("guest", encoder.encode("pw"))));
        assertThat(supportService.verifyGuest(1L, "guest", "pw")).isTrue();
    }

    @Test
    void verifyGuest_비번이_틀리면_false() {
        when(supportRepository.findById(1L)).thenReturn(Optional.of(post("guest", encoder.encode("pw"))));
        assertThat(supportService.verifyGuest(1L, "guest", "wrong")).isFalse();
    }

    @Test
    void verifyGuest_글이_없으면_false() {
        when(supportRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(supportService.verifyGuest(9L, "guest", "pw")).isFalse();
    }

    @Test
    void verifyGuest_평문으로_저장된_예전_글도_확인되고_해시로_바뀐다() {
        SupportEntity legacy = post("guest", "pw");
        when(supportRepository.findById(1L)).thenReturn(Optional.of(legacy));

        assertThat(supportService.verifyGuest(1L, "guest", "pw")).isTrue();
        assertThat(legacy.getGuestPassword()).startsWith("$2");
        assertThat(encoder.matches("pw", legacy.getGuestPassword())).isTrue();
    }

    @Test
    void verifyGuest_다섯번_틀리면_맞는_비번이어도_잠긴다() {
        when(supportRepository.findById(1L)).thenReturn(Optional.of(post("guest", encoder.encode("pw"))));
        for (int i = 0; i < 5; i++) {
            assertThat(supportService.verifyGuest(1L, "guest", "wrong")).isFalse();
        }

        assertThatThrownBy(() -> supportService.verifyGuest(1L, "guest", "pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPORT_AUTH_LOCKED);
    }
}
