package net.datasa.tanoshimi.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.oauth.PendingSocialSignup;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthViewController {

    private final HttpSession httpSession;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) return "redirect:/";
        model.addAttribute("errorMessage", error);
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage(Authentication authentication) {
        if (isLoggedIn(authentication)) return "redirect:/";
        return "auth/signup";
    }

    @GetMapping("/signup/social")
    public String socialSignupPage(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) return "redirect:/";
        Object pending = httpSession.getAttribute(PendingSocialSignup.SESSION_KEY);
        if (!(pending instanceof PendingSocialSignup signup)) {
            return "redirect:/login?error=소셜 로그인 정보가 없습니다. 다시 시도해 주세요.";
        }
        model.addAttribute("email", signup.email());
        model.addAttribute("needEmailInput", signup.email() == null);
        model.addAttribute("suggestedName", signup.name());
        model.addAttribute("providerName", signup.provider());
        return "auth/signup-social";
    }

    @GetMapping("/signup/complete")
    public String signupComplete() { return "auth/signup-complete"; }

    @GetMapping("/error/403")
    public String accessDenied() { return "error/403"; }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}
