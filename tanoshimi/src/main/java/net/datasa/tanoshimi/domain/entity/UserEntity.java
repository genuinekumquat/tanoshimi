package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 회원 엔티티.
 *
 * <p>보안 메모: password 는 BCrypt 해시만 저장. 소셜 전용 계정은 로그인 불가능한
 * 랜덤 문자열의 해시가 들어간다. toString 에는 password/phone 을 포함하지 않는다.
 */
@Entity
@Getter
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "email", "name", "role", "status", "nationality"})
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** [vanity-url 신규] 프로필 URL(/{username}) 용 아이디 - 항상 소문자로 정규화되어 저장된다.
     * 형식/예약어 규칙은 UsernamePolicy 참고. */
    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Nationality nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_lang", nullable = false, length = 5)
    private PreferredLang preferredLang;

    /**
     * 매너온도. v16 이전엔 mannerScore(float)/mannerTemp(BigDecimal) 두 필드가 중복으로
     * 존재했다(기술부채) - v16 DB설계에서 하나로 통합하기로 확정되어 mannerScore 는 삭제하고
     * 이 필드로 일원화한다. 정책·계산·증감 트리거는 MannerTempService 가 단독 소유하며,
     * 이 엔티티는 0~50 범위 캡만 스스로 보장한다(applyMannerDelta 참고).
     */
    @Column(name = "manner_temp", nullable = false)
    private java.math.BigDecimal mannerTemp;

    @Column(name = "points_krw", nullable = false)
    private int pointsKrw;

    @Column(name = "points_jpy", nullable = false)
    private int pointsJpy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(length = 300)
    private String intro;

    @Column(name = "social_provider", length = 20)
    private String socialProvider;

    @Column(name = "social_id", length = 255)
    private String socialId;

    /** [account-settings 신규] 계정 공개범위. true=비공개(다른 사용자는 /users/{id} 열람 불가), false=공개(기본값). */
    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private UserEntity(String email, String username, String password, String name, String phone, Gender gender,
                       LocalDate birthDate, Nationality nationality, Role role, UserStatus status,
                       String socialProvider, String socialId) {
        this.email = email;
        this.username = username;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.phoneVerified = true;
        this.gender = gender;
        this.birthDate = birthDate;
        this.nationality = nationality;
        this.preferredLang = nationality == Nationality.JP ? PreferredLang.ja : PreferredLang.ko;
        this.mannerTemp = new java.math.BigDecimal("36.5");
        this.pointsKrw = 0;
        this.pointsJpy = 0;
        this.role = role;
        this.status = status;
        this.socialProvider = socialProvider;
        this.socialId = socialId;
    }

    public static UserEntity createLocal(String email, String username, String encodedPassword, String name, String phone,
                                         Gender gender, LocalDate birthDate, Nationality nationality) {
        return UserEntity.builder()
                .email(email).username(username).password(encodedPassword).name(name).phone(phone)
                .gender(gender).birthDate(birthDate).nationality(nationality)
                .role(Role.user).status(UserStatus.active).build();
    }

    public static UserEntity createSocial(String email, String username, String unusablePasswordHash, String name, String phone,
                                          Gender gender, LocalDate birthDate, Nationality nationality,
                                          String provider, String socialId) {
        return UserEntity.builder()
                .email(email).username(username).password(unusablePasswordHash).name(name).phone(phone)
                .gender(gender).birthDate(birthDate).nationality(nationality)
                .role(Role.user).status(UserStatus.active)
                .socialProvider(provider).socialId(socialId).build();
    }

    public boolean isSocialAccount() { return socialProvider != null; }

    /**
     * [social-link 신규] 이미 존재하는 계정(보통 로컬 계정)에 소셜 계정을 연동한다.
     * 비밀번호는 절대 건드리지 않는다. 한 번 연동되면 UI 로는 해제할 수 없다(영구 연동 -
     * 코디네이터 지시로 "연동 해제" 기능 자체를 없앴다).
     */
    public void linkSocial(String provider, String socialId) {
        this.socialProvider = provider;
        this.socialId = socialId;
    }
        public boolean isActive() {
        if (status == UserStatus.suspended) {
            if (suspendedUntil != null && LocalDateTime.now().isAfter(suspendedUntil)) {
                return true;
            }
            return false;
        }
        return status.isActive();
    }
    
    public boolean isAdmin() { return role == Role.admin; }
    public void grantAdmin() { this.role = Role.admin; }
    public void revokeAdmin() { this.role = Role.user; }


    /** 만 나이 계산 - 회원가입 시 성인 인증에 사용. */
    public int age() {
        return java.time.Period.between(birthDate, LocalDate.now()).getYears();
    }

    public void changePassword(String encoded) { this.password = encoded; }
    public void changeProfile(String name, String intro, String profileImageUrl) {
        this.name = name;
        this.intro = intro;
        this.profileImageUrl = profileImageUrl;
    }

    public void changePreferredLang(PreferredLang lang) { this.preferredLang = lang; }

    /** [account-settings 신규] 계정 공개범위 변경. Lombok @Getter 가 필드명이 이미 "is"로 시작하므로
     * isPrivate() 게터를 자동 생성해준다(수동으로 추가하면 중복 정의가 된다). */
    public void changeVisibility(boolean isPrivate) { this.isPrivate = isPrivate; }

    /**
     * [account-settings 신규] 회원정보조회 탭의 "수정" - 이름/전화/성별/생년월일/국적을 한 번에 바꾼다.
     * 이메일은 로그인 ID 라 여기서 다루지 않는다(가입 후 불변 - SignupRequest 에도 이메일 변경 경로가 없다).
     * 유효성(전화번호 형식 등)은 호출부(UserService)가 SignupRequest 와 같은 규칙으로 먼저 검증한다.
     */
    public void changePersonalInfo(String name, String phone, Gender gender, LocalDate birthDate, Nationality nationality) {
        this.name = name;
        this.phone = phone;
        this.gender = gender;
        this.birthDate = birthDate;
        this.nationality = nationality;
    }
    
    
    public void suspend(LocalDateTime until) {
        this.status = UserStatus.suspended;
        this.suspendedUntil = until;
    }
    public void activate() {
        this.status = UserStatus.active;
        this.suspendedUntil = null;
    }
    public boolean isCurrentlySuspended() {
        if (status != UserStatus.suspended) return false;
        if (suspendedUntil != null && LocalDateTime.now().isAfter(suspendedUntil)) {
            return false;
        }
        return true;
    }


    public void addPoints(Currency currency, int amount) {
        if (currency == Currency.KRW) this.pointsKrw += amount;
        else this.pointsJpy += amount;
    }

    /** 결제 시 포인트 차감. 잔액 부족하면 false. */
    public boolean deductPoints(Currency currency, int amount) {
        if (currency == Currency.KRW) {
            if (pointsKrw < amount) return false;
            pointsKrw -= amount;
        } else {
            if (pointsJpy < amount) return false;
            pointsJpy -= amount;
        }
        return true;
    }

    /**
     * 매너온도 증감 - 반드시 이 메서드를 통해서만 mannerTemp 를 바꾼다(직접 대입 금지).
     * 0~50 범위를 벗어나는 가산/감산은 경계값으로 캡핑한다(v16 필드제약조건 확정 사항).
     * 실제로 언제/왜 이 메서드를 호출할지에 대한 정책은 이 엔티티가 아니라
     * MannerTempService 가 단독으로 결정한다 - 여기는 캡핑 규칙만 보장한다.
     */
    public void applyMannerDelta(java.math.BigDecimal delta) {
        java.math.BigDecimal next = this.mannerTemp.add(delta);
        if (next.compareTo(java.math.BigDecimal.ZERO) < 0) {
            next = java.math.BigDecimal.ZERO;
        } else if (next.compareTo(new java.math.BigDecimal("50.0")) > 0) {
            next = new java.math.BigDecimal("50.0");
        }
        this.mannerTemp = next.setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
