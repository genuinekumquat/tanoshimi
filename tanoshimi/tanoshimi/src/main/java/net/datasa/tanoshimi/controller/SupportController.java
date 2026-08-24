package net.datasa.tanoshimi.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.SupportCommentEntity;
import net.datasa.tanoshimi.domain.entity.SupportEntity;
import net.datasa.tanoshimi.repository.SupportCommentRepository;
import net.datasa.tanoshimi.repository.SupportRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportRepository supportRepository;
    private final SupportCommentRepository supportCommentRepository;

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal CustomUserDetails principal) {
        List<SupportEntity> posts = supportRepository.findAllByOrderByIdDesc();
        boolean isAdmin = (principal != null && principal.isAdmin());
        model.addAttribute("posts", posts);
        model.addAttribute("isAdmin", isAdmin);
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
        SupportEntity post = SupportEntity.builder()
                .guestId(guestId)
                .guestPassword(guestPassword)
                .title(title)
                .content(content)
                .build();
        supportRepository.save(post);
        return "redirect:/support";
    }

    @GetMapping("/{id}")
    public String authForm(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, HttpSession session, Model model) {
        if (principal != null && principal.isAdmin()) {
            return "redirect:/support/" + id + "/view";
        }
        
        // If already authenticated this session for this post
        Boolean authenticated = (Boolean) session.getAttribute("support_auth_" + id);
        if (Boolean.TRUE.equals(authenticated)) {
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
        SupportEntity post = supportRepository.findById(id).orElse(null);
        if (post != null && post.getGuestId().equals(guestId) && post.getGuestPassword().equals(guestPassword)) {
            session.setAttribute("support_auth_" + id, true);
            return "redirect:/support/" + id + "/view";
        }
        rttr.addFlashAttribute("error", "아이디 또는 비밀번호가 일치하지 않습니다.");
        return "redirect:/support/" + id;
    }

    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, HttpSession session, Model model) {
        SupportEntity post = supportRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid post ID"));
        
        boolean isAdmin = (principal != null && principal.isAdmin());
        boolean isAuthenticated = Boolean.TRUE.equals(session.getAttribute("support_auth_" + id));
        
        if (!isAdmin && !isAuthenticated) {
            return "redirect:/support/" + id;
        }

        List<SupportCommentEntity> comments = supportCommentRepository.findBySupportIdAndParentCommentIsNullOrderByCreatedAtAsc(id);

        model.addAttribute("post", post);
        model.addAttribute("comments", comments);
        return "support/detail";
    }

    @PostMapping("/{id}/comment")
    public String addComment(@PathVariable Long id,
                             @RequestParam String content,
                             @RequestParam(required = false) Long parentId,
                             @AuthenticationPrincipal CustomUserDetails principal,
                             HttpSession session) {
        SupportEntity post = supportRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid post ID"));

        boolean isAdmin = (principal != null && principal.isAdmin());
        boolean isAuthenticated = Boolean.TRUE.equals(session.getAttribute("support_auth_" + id));

        if (!isAdmin && !isAuthenticated) {
            return "redirect:/support/" + id;
        }

        String writerName = isAdmin ? "관리자" : post.getGuestId();

        SupportCommentEntity parent = null;
        if (parentId != null) {
            parent = supportCommentRepository.findById(parentId).orElse(null);
        }

        SupportCommentEntity comment = SupportCommentEntity.builder()
                .support(post)
                .content(content)
                .writerName(writerName)
                .parentComment(parent)
                .build();
        
        supportCommentRepository.save(comment);

        return "redirect:/support/" + id + "/view";
    }
}
