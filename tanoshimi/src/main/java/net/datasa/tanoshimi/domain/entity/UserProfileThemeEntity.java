package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** [v16 신규] 마이페이지 프로필 배경/스킨 꾸미기(미니홈피 스타일) 설정. 회원당 1행. */
@Entity
@Getter
@Table(name = "user_profile_theme")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileThemeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    /** 사전 정의된 배경/스킨 키 (예: forest_green, sakura_pink 등 - 프론트에서 매핑). */
    @Column(name = "theme_key", nullable = false, length = 50)
    private String themeKey;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserProfileThemeEntity(UserEntity user, String themeKey) {
        this.user = user;
        this.themeKey = themeKey;
    }

    public void changeTheme(String themeKey) { this.themeKey = themeKey; }
}
