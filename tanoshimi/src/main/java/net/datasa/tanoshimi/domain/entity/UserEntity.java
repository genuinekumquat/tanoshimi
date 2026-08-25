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

    @Column(name = "manner_score", nullable = false, columnDefinition = "float default 36.5")
    private float mannerScore = 36.5f;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Nationality nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_lang", nullable = false, length = 5)
    private PreferredLang preferredLang;

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
}
