package net.datasa.tanoshimi.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.SupportEntity;
import net.datasa.tanoshimi.service.SupportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal CustomUserDetails principal) {
        model.addAttribute("posts", supportService.listNewestFirst());
        model.addAttribute("isAdmin", principal != null && principal.isAdmin());
        return "support/list";
    }

    @GetMapping("/write")
    public String writeForm() {
        return "support/write";
    }

    @PostMapping("/write")
    public String writeSubmit(
            @RequestParam String guestId,
            @RequestParam String guestPassword,
            @RequestParam String title,
            @RequestParam String content) {
        supportService.write(guestId, guestPassword, title, content);
        return "redirect:/support";
    }

    @GetMapping("/{id}")
    public String authForm(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, HttpSession session, Model model) {
        if (principal != null && principal.isAdmin()) {
            return "redirect:/support/" + id + "/view";
        }

        // If already authenticated this session for this post
        if (Boolean.TRUE.equals(session.getAttribute("support_auth_" + id))) {
            return "redirect:/support/" + id + "/view";
        }

        model.addAttribute("id", id);
        return "support/auth";
    }

    @PostMapping("/{id}/auth")
    public String authorize(@PathVariable Long id,
                            @RequestParam String guestId,
                            @RequestParam String guestPassword,
                            HttpSession session,
                            RedirectAttributes rttr) {
        if (supportService.verifyGuest(id, guestId, guestPassword)) {
            session.setAttribute("support_auth_" + id, true);
            return "redirect:/support/" + id + "/view";
        }
        rttr.addFlashAttribute("error", "아이디 또는 비밀번호가 일치하지 않습니다.");
        return "redirect:/support/" + id;
    }

    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, HttpSession session, Model model) {
        if (!canRead(id, principal, session)) {
            return "redirect:/support/" + id;
        }
        model.addAttribute("post", supportService.get(id));
        model.addAttribute("comments", supportService.topLevelComments(id));
        return "support/detail";
    }

    @PostMapping("/{id}/comment")
    public String addComment(@PathVariable Long id,
                             @RequestParam String content,
                             @RequestParam(required = false) Long parentId,
                             @AuthenticationPrincipal CustomUserDetails principal,
                             HttpSession session) {
        if (!canRead(id, principal, session)) {
            return "redirect:/support/" + id;
        }
        boolean isAdmin = principal != null && principal.isAdmin();
        SupportEntity post = supportService.get(id);
        String writerName = isAdmin ? "관리자" : post.getGuestId();
        supportService.addComment(id, content, parentId, writerName);
        return "redirect:/support/" + id + "/view";
    }

    /** 관리자이거나, 이 세션에서 비회원 본인 확인을 통과한 경우에만 문의글을 읽을 수 있다. */
    private boolean canRead(Long id, CustomUserDetails principal, HttpSession session) {
        boolean isAdmin = principal != null && principal.isAdmin();
        boolean isAuthenticated = Boolean.TRUE.equals(session.getAttribute("support_auth_" + id));
        return isAdmin || isAuthenticated;
    }
}
