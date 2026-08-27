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

    // v16 제안서 기준 매너온도는 mannerTemp(BigDecimal) 하나로 통일 (아래 참고).
    // mannerScore 는 이전 패치 스크립트가 남긴 중복 필드라 매핑 해제만 하고 컬럼/DB 기본값은 그대로 둔다.
    // (컬럼에 NOT NULL default 36.5 가 걸려 있어 INSERT 시 이 필드를 빼도 DB가 알아서 채운다)
    // @Column(name = "manner_score", nullable = false, columnDefinition = "float default 36.5")
    // private float mannerScore = 36.5f;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Nationality nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_lang", nullable = false, length = 5)
    private PreferredLang preferredLang;

    /**
     * 매너온도. v16 이전엔 mannerScore(float)/mannerTemp(BigDecimal) 두 필드가 중복으로
     * 존재했다(기술부채) - v16 DB설계에서 하나로 통합하기로 확정되어 mannerScore 는 삭제하고
     * 이 필드로 일원화한다. 정책·계산·증감 트리거는 MannerTempService 가 단독 소유하며(⑤),
     * 회원가입 시 초기값 대입만 ①(인증·회원가입)이 담당한다. 이 엔티티는 0~50 범위 캡만
     * 스스로 보장한다(applyMannerDelta 참고).
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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private UserEntity(String email, String password, String name, String phone, Gender gender,
                       LocalDate birthDate, Nationality nationality, Role role, UserStatus status,
                       String socialProvider, String socialId) {
        this.email = email;
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

    public static UserEntity createLocal(String email, String encodedPassword, String name, String phone,
                                         Gender gender, LocalDate birthDate, Nationality nationality) {
        return UserEntity.builder()
                .email(email).password(encodedPassword).name(name).phone(phone)
                .gender(gender).birthDate(birthDate).nationality(nationality)
                .role(Role.user).status(UserStatus.active).build();
    }

    public static UserEntity createSocial(String email, String unusablePasswordHash, String name, String phone,
                                          Gender gender, LocalDate birthDate, Nationality nationality,
                                          String provider, String socialId) {
        return UserEntity.builder()
                .email(email).password(unusablePasswordHash).name(name).phone(phone)
                .gender(gender).birthDate(birthDate).nationality(nationality)
                .role(Role.user).status(UserStatus.active)
                .socialProvider(provider).socialId(socialId).build();
    }

    public boolean isSocialAccount() { return socialProvider != null; }

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