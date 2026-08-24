package net.datasa.tanoshimi.auth;

import net.datasa.tanoshimi.domain.entity.Role;
import net.datasa.tanoshimi.domain.entity.PreferredLang;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class CustomUserDetails implements UserDetails, OAuth2User {

    private final Long id;
    private final String email;
    private final String password;
    private final String name;
    private final Role role;
    private final boolean active;
    private final PreferredLang preferredLang;
    private final String profileImageUrl;
    private final Map<String, Object> attributes;

    public CustomUserDetails(UserEntity user) { this(user, Map.of()); }

    public CustomUserDetails(UserEntity user, Map<String, Object> attributes) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.name = user.getName();
        this.role = user.getRole();
        this.active = user.isActive();
        this.preferredLang = user.getPreferredLang();
        this.profileImageUrl = user.getProfileImageUrl();
        this.attributes = attributes == null ? Map.of() : attributes;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return name; }
    public Role getRole() { return role; }
    public PreferredLang getPreferredLang() { return preferredLang; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public boolean isAdmin() { return role == Role.admin; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.key()));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public Map<String, Object> getAttributes() { return attributes; }
    @Override public String getName() { return String.valueOf(id); }
}