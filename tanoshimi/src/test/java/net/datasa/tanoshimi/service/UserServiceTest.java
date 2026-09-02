package net.datasa.tanoshimi.service;
import net.datasa.tanoshimi.auth.oauth.PendingSocialSignup;
import net.datasa.tanoshimi.domain.dto.SignupRequest;
import net.datasa.tanoshimi.domain.dto.SocialSignupRequest;
import net.datasa.tanoshimi.domain.entity.Role;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserStatus;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * 회원가입은 이메일/전화 중복 체크와 이메일 인증 소진이 반드시 먼저 일어나야 하고,
 * role/status 는 외부 입력을 받지 않는 것(권한 상승 방지)이 핵심 계약이라 이 부분들을 검증한다.
 * (본인인증 채널은 알리고 SMS의 사업자등록번호 이슈로 이메일로 전환됨 - EmailVerificationService)
 * PasswordEncoder 는 실제 BCryptPasswordEncoder 를 사용해, 소셜 계정의 "사용 불가능한 비밀번호"가
 * 정말로 어떤 값으로도 매치되지 않는지까지 확인한다.
 *
 * <p>v18: 가입 시 TitleService 로 NEWBIE 칭호를 자동 부여하던 동작은 v17(38종 칭호 체계 개편,
 * UserService.java 주석 참고)에서 이미 사라졌다 - 38종 체계엔 "가입만 하면 받는" 칭호가 없고
 * 가장 낮은 등급도 "여행 1회"부터라서다. 이 테스트는 그 옛 동작 기준으로 작성된 채 갱신되지
 * 않고 남아 있었던 것이라, UserService 생성자/TitleService mock 을 현재 시그니처에 맞춰 정리했다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationService emailVerificationService;
    private UserService userService;
    private static final String RAW_PASSWORD = "Abc12345!";
    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new BCryptPasswordEncoder(4), emailVerificationService);
    }
    private SignupRequest validSignupRequest() {
        return new SignupRequest(
                "  User@Test.com ", RAW_PASSWORD, RAW_PASSWORD, "유자차",
                "010-1111-2222", "female", LocalDate.of(1998, 5, 14), "KR", true);
    }
    private SocialSignupRequest validSocialSignupRequest(String email) {
        return new SocialSignupRequest("유자차", email, "010-1111-2222", "female",
                LocalDate.of(1998, 5, 14), "KR", true);
    }
    // ---------------------------------------------------------------- signup
    @Test
    void signup_성공하면_중복확인과_이메일인증_소진_후_저장한다() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("01011112222")).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        Long userId = userService.signup(validSignupRequest());
        assertThat(userId).isEqualTo(1L);
        verify(emailVerificationService).consumeVerified("user@test.com", VerificationPurpose.signup);
    }
    @Test
    void signup_이메일과_전화번호를_정규화한_뒤_중복확인한다() {
        // 입력은 공백/대문자/하이픈이 섞여 있지만, 서비스가 trim+lowercase, 숫자만 남기기를 해서 조회해야 한다.
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        userService.signup(validSignupRequest());
        verify(userRepository).existsByEmail("user@test.com");
        verify(userRepository).existsByPhone("01011112222");
    }
    @Test
    void signup_이메일이_이미_존재하면_DUPLICATE_EMAIL_예외를_던지고_더_진행하지_않는다() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.signup(validSignupRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        // 이메일 중복이면 이메일 인증 소진이나 저장까지 가면 안 된다 (자원 낭비 + 잘못된 인증 소모 방지)
        verify(emailVerificationService, never()).consumeVerified(anyString(), any());
        verify(userRepository, never()).saveAndFlush(any());
    }
    @Test
    void signup_전화번호가_이미_존재하면_DUPLICATE_PHONE_예외를_던진다() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("01011112222")).thenReturn(true);
        assertThatThrownBy(() -> userService.signup(validSignupRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PHONE);
        verify(emailVerificationService, never()).consumeVerified(anyString(), any());
    }
    @Test
    void signup_이메일_인증이_확인되지_않았으면_가입이_저장되지_않는다() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.VERIFICATION_REQUIRED))
                .when(emailVerificationService).consumeVerified(anyString(), any());
        assertThatThrownBy(() -> userService.signup(validSignupRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_REQUIRED);
        verify(userRepository, never()).saveAndFlush(any());
    }
    @Test
    void signup_요청에_role이나_status_필드가_없어도_항상_일반회원_활성상태로_저장된다() {
        // SignupRequest 자체에 role/status 필드가 없어 컴파일 타임에 이미 막혀 있지만,
        // 실제로 저장되는 UserEntity 가 항상 안전한 기본값인지 한 번 더 확인한다(권한 상승 방지).
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        userService.signup(validSignupRequest());
        UserEntity saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.user);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.active);
        assertThat(saved.isSocialAccount()).isFalse();
    }
    @Test
    void signup_비밀번호는_평문으로_저장되지_않고_인코딩된_해시만_저장된다() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        userService.signup(validSignupRequest());
        String storedHash = captor.getValue().getPassword();
        assertThat(storedHash).isNotEqualTo(RAW_PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(RAW_PASSWORD, storedHash)).isTrue();
    }
    @Test
    void signup_저장_시점에_유니크_제약_위반이_나면_DUPLICATE_EMAIL로_변환된다() {
        // 두 사용자가 동시에 같은 이메일로 가입 시도할 때, existsByEmail 체크는 통과했지만
        // 실제 저장 시점(DB UNIQUE 제약)에서 충돌하는 경쟁 상태를 흉내낸다.
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));
        assertThatThrownBy(() -> userService.signup(validSignupRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
    // ------------------------------------------------------------ signupSocial
    @Test
    void signupSocial_세션의_pending_이메일을_우선_사용한다() {
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-1", "pending@test.com", "유자차");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        // 요청에 사용자가 다른 이메일을 적어 냈어도, pending(세션) 값이 우선이어야 한다(클라이언트 위조 방지).
        userService.signupSocial(pending, validSocialSignupRequest("spoofed@test.com"));
        assertThat(captor.getValue().getEmail()).isEqualTo("pending@test.com");
        verify(userRepository).existsByEmail("pending@test.com");
    }
    @Test
    void signupSocial_소셜제공자가_이메일을_준_경우엔_이메일인증을_요구하지_않는다() {
        // 구글/네이버는 pending.email() 이 채워져서 온다 - 소셜 로그인 자체가 이미 신뢰할 수
        // 있는 인증 수단이라 인증코드를 또 보내 확인시킬 필요가 없다.
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-1", "pending@test.com", "유자차");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.signupSocial(pending, validSocialSignupRequest(null));

        verify(emailVerificationService, never()).consumeVerified(anyString(), any());
    }

    @Test
    void signupSocial_LINE처럼_이메일을_직접_입력한_경우에도_이메일인증을_요구하지_않는다() {
        // LINE은 이메일 스코프가 없어 pending.email() 이 null이라 사용자가 화면에서 직접
        // 입력하지만, 소셜 로그인 자체를 신뢰하는 정책이라 이 경우도 인증코드는 요구하지 않는다
        // (대신 형식 검증은 SocialSignupRequest 의 @Email 로 최소한 걸러진다).
        PendingSocialSignup pending = new PendingSocialSignup("line", "social-id-2", null, "유자차");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        userService.signupSocial(pending, validSocialSignupRequest("user@test.com"));
        verify(userRepository).existsByEmail("user@test.com");
        verify(emailVerificationService, never()).consumeVerified(anyString(), any());
    }
    @Test
    void signupSocial_이메일을_어디서도_구할_수_없으면_예외를_던진다() {
        PendingSocialSignup pending = new PendingSocialSignup("line", "social-id-3", null, "유자차");
        assertThatThrownBy(() -> userService.signupSocial(pending, validSocialSignupRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(userRepository, never()).existsByEmail(anyString());
    }
    @Test
    void signupSocial_이메일이_이미_가입되어_있으면_DUPLICATE_EMAIL_예외() {
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-4", "user@test.com", "유자차");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.signupSocial(pending, validSocialSignupRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
    @Test
    void signupSocial_저장된_계정은_소셜정보를_담고_비밀번호로는_절대_로그인할_수_없다() {
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-5", "user@test.com", "유자차");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        when(userRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        userService.signupSocial(pending, validSocialSignupRequest(null));
        UserEntity saved = captor.getValue();
        assertThat(saved.getSocialProvider()).isEqualTo("google");
        assertThat(saved.getSocialId()).isEqualTo("social-id-5");
        assertThat(saved.isSocialAccount()).isTrue();
        // 랜덤 해시라 원문을 알 수 없으니, 흔히 시도할 만한 값들로 매치가 안 되는지만 확인한다.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("", saved.getPassword())).isFalse();
        assertThat(encoder.matches("password", saved.getPassword())).isFalse();
        assertThat(encoder.matches(RAW_PASSWORD, saved.getPassword())).isFalse();
    }
    @Test
    void signupSocial_전화번호가_이미_존재하면_DUPLICATE_PHONE_예외() {
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-6", "user@test.com", "유자차");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone("01011112222")).thenReturn(true);
        assertThatThrownBy(() -> userService.signupSocial(pending, validSocialSignupRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PHONE);
    }
    // ------------------------------------------------------------ isEmailAvailable
    @Test
    void isEmailAvailable_사용중인_이메일이면_false() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);
        assertThat(userService.isEmailAvailable("  User@Test.com ")).isFalse();
    }
    @Test
    void isEmailAvailable_공백이나_null이면_저장소를_조회하지_않고_false() {
        assertThat(userService.isEmailAvailable(null)).isFalse();
        assertThat(userService.isEmailAvailable("   ")).isFalse();
        verify(userRepository, never()).existsByEmail(anyString());
    }
}
