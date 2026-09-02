package net.datasa.tanoshimi.auth;

import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 원래 비밀번호와, 비밀번호 재발급으로 대기 중인 임시 비밀번호를 둘 다 로그인에 허용하고,
 * 실제로 임시 비밀번호로 로그인에 성공하는 순간에만 password로 승격시키는 핵심 로직을 검증한다.
 * BCryptPasswordEncoder는 실제 구현을 사용해 인코딩/매칭이 진짜로 맞물리는지까지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class TempPasswordAuthenticationProviderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetailsService userDetailsService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private TempPasswordAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TempPasswordAuthenticationProvider(userDetailsService, passwordEncoder, userRepository);
    }

    private UserEntity localUser(String email, String rawPassword) {
        return UserEntity.createLocal(email, passwordEncoder.encode(rawPassword), "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR);
    }

    @Test
    void 원래_비밀번호가_맞으면_그대로_통과하고_DB를_다시_조회하지_않는다() {
        UserEntity user = localUser("user@test.com", "OldPass12!");
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(details.getUsername(), "OldPass12!");

        provider.additionalAuthenticationChecks(details, token);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void 원래_비밀번호가_틀려도_대기중인_임시비밀번호가_맞으면_승격시키고_통과한다() {
        UserEntity user = localUser("user@test.com", "OldPass12!");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.issueTemporaryPassword(passwordEncoder.encode("Temp123!"), LocalDateTime.now().plusMinutes(30));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(details.getUsername(), "Temp123!");

        provider.additionalAuthenticationChecks(details, token);

        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("Temp123!", user.getPassword())).isTrue();
        // 한 번 쓴 임시 비밀번호는 재사용할 수 없어야 한다.
        assertThat(user.hasValidPendingTempPassword()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void 원래_비밀번호를_써도_대기중인_임시비밀번호가_그대로_남아있는다() {
        // 재발급을 요청해놓고도 원래 비밀번호로 로그인하는 경우 - 대기 중인 임시 비밀번호는
        // 나중에라도 쓸 수 있게 건드리지 않아야 한다(만료 전까지).
        UserEntity user = localUser("user@test.com", "OldPass12!");
        user.issueTemporaryPassword(passwordEncoder.encode("Temp123!"), LocalDateTime.now().plusMinutes(30));

        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(details.getUsername(), "OldPass12!");

        provider.additionalAuthenticationChecks(details, token);

        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.hasValidPendingTempPassword()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void 원래_비밀번호도_임시비밀번호도_아니면_BadCredentialsException을_던지고_승격시키지_않는다() {
        UserEntity user = localUser("user@test.com", "OldPass12!");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.issueTemporaryPassword(passwordEncoder.encode("Temp123!"), LocalDateTime.now().plusMinutes(30));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(details.getUsername(), "WrongPass1!");

        assertThatThrownBy(() -> provider.additionalAuthenticationChecks(details, token))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.isMustChangePassword()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void 대기중인_임시비밀번호가_만료됐으면_실패한다() {
        UserEntity user = localUser("user@test.com", "OldPass12!");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.issueTemporaryPassword(passwordEncoder.encode("Temp123!"), LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(details.getUsername(), "Temp123!");

        assertThatThrownBy(() -> provider.additionalAuthenticationChecks(details, token))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void createSuccessAuthentication은_승격_이후_최신_DB상태를_principal에_반영한다() {
        UserEntity user = localUser("user@test.com", "OldPass12!");
        ReflectionTestUtils.setField(user, "id", 1L);
        // additionalAuthenticationChecks 가 만들었을 "승격 전" 스냅샷 - mustChangePassword=false.
        CustomUserDetails staleDetails = new CustomUserDetails(user);

        // 실제로는 additionalAuthenticationChecks 안에서 일어나는 승격을 흉내낸다.
        user.issueTemporaryPassword(passwordEncoder.encode("Temp123!"), LocalDateTime.now().plusMinutes(30));
        user.promoteTemporaryPassword();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Authentication token = UsernamePasswordAuthenticationToken.unauthenticated(staleDetails, "Temp123!");

        Authentication result = provider.createSuccessAuthentication(staleDetails, token, staleDetails);

        assertThat(staleDetails.isMustChangePassword()).isFalse(); // 스냅샷 자체는 그대로 stale
        CustomUserDetails resultPrincipal = (CustomUserDetails) result.getPrincipal();
        assertThat(resultPrincipal.isMustChangePassword()).isTrue(); // 최종 principal은 최신 상태
    }
}
