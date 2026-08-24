package net.datasa.tanoshimi.util;

import java.util.Optional;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 팀 공용 유틸. "지금 로그인한 사람이 누구인지" 를 어디서든 같은 방식으로 얻기 위한 것. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<CustomUserDetails> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
        if (authentication.getPrincipal() instanceof CustomUserDetails user) return Optional.of(user);
        return Optional.empty();
    }

    public static Optional<Long> currentUserId() { return currentUser().map(CustomUserDetails::getId); }
    public static boolean isLoggedIn() { return currentUser().isPresent(); }
    public static boolean isAdmin() { return currentUser().map(CustomUserDetails::isAdmin).orElse(false); }

    public static boolean canAccess(Long ownerUserId) {
        if (ownerUserId == null) return false;
        return currentUser().map(u -> u.isAdmin() || ownerUserId.equals(u.getId())).orElse(false);
    }
}
