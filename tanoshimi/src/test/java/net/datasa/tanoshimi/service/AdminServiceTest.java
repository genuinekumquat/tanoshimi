package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.repository.ReportRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PartyRepository partyRepository;
    @Mock private ReportRepository reportRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void suspendUser_자기_자신은_정지하지_않는다() {
        UserEntity self = mock(UserEntity.class);
        when(self.getId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        adminService.suspendUser(1L, 7, 1L);

        verify(self, never()).suspend(any());
    }

    @Test
    void suspendUser_duration_999_는_영구정지_1_은_24시간_그외는_일수() {
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));
        ArgumentCaptor<LocalDateTime> cap = ArgumentCaptor.forClass(LocalDateTime.class);

        adminService.suspendUser(2L, 999, 1L);
        adminService.suspendUser(2L, 1, 1L);
        adminService.suspendUser(2L, 3, 1L);

        verify(u, times(3)).suspend(cap.capture());
        LocalDateTime now = LocalDateTime.now();
        assertThat(cap.getAllValues().get(0)).isAfter(now.plusYears(900));      // 999년
        assertThat(cap.getAllValues().get(1)).isBetween(now.plusHours(23), now.plusHours(25)); // 24h
        assertThat(cap.getAllValues().get(2)).isBetween(now.plusDays(2), now.plusDays(4));     // 3일
    }

    @Test
    void revokeAdmin_자기_자신은_회수하지_않는다() {
        UserEntity self = mock(UserEntity.class);
        when(self.getId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        adminService.revokeAdmin(1L, 1L);

        verify(self, never()).revokeAdmin();
    }
}
