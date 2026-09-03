package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.ReportActionTaken;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.AdminService;
import net.datasa.tanoshimi.service.BannerService;
import net.datasa.tanoshimi.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final AdminService adminService;
    private final BannerService bannerService;
    private final ReportService reportService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal CustomUserDetails admin,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("users", adminService.pageUsers(keyword, page));
        model.addAttribute("adminName", admin.getDisplayName());
        model.addAttribute("banners", bannerService.list());
        // 대기 중인 신고 개수 - 관리자 메뉴에 뱃지로 보여주기 위함
        model.addAttribute("pendingReportCount", adminService.pendingReportCount());
        return "admin/users";
    }

    @PostMapping("/users/{id}/suspend")
    public String suspend(@PathVariable Long id, @RequestParam int duration, @AuthenticationPrincipal CustomUserDetails admin) {
        adminService.suspendUser(id, duration, admin.getId());
        return "redirect:/admin";
    }

    @PostMapping("/users/{id}/activate")
    public String activate(@PathVariable Long id) {
        adminService.activateUser(id);
        return "redirect:/admin";
    }

    @GetMapping("/parties")
    public String parties(@RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "0") int page,
                          Model model,
                          @AuthenticationPrincipal CustomUserDetails admin) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("parties", adminService.pageParties(keyword, page));
        model.addAttribute("adminName", admin.getDisplayName());
        return "admin/parties";
    }

    @PostMapping("/parties/{id}/close")
    public String closeParty(@PathVariable Long id) {
        adminService.closeParty(id);
        return "redirect:/admin/parties";
    }

    @PostMapping("/users/{id}/grant-admin")
    public String grantAdmin(@PathVariable Long id) {
        adminService.grantAdmin(id);
        return "redirect:/admin";
    }

    @PostMapping("/users/{id}/revoke-admin")
    public String revokeAdmin(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        adminService.revokeAdmin(id, admin.getId());
        return "redirect:/admin";
    }

    // ============================================
    // BANNERS
    // ============================================
    @GetMapping("/banners")
    public String banners(@AuthenticationPrincipal CustomUserDetails admin, Model model) {
        model.addAttribute("adminName", admin.getDisplayName());
        model.addAttribute("banners", bannerService.list());
        return "admin/banners";
    }

    @PostMapping("/banners")
    public String uploadBanner(@RequestParam MultipartFile file, @RequestParam(required = false) String targetUrl) {
        boolean ok = bannerService.upload(file, targetUrl);
        return ok ? "redirect:/admin/banners" : "redirect:/admin/banners?error=bannerLimit";
    }

    @PostMapping("/banners/{id}/delete")
    public String deleteBanner(@PathVariable Long id) {
        bannerService.delete(id);
        return "redirect:/admin/banners";
    }

    // ============================================
    // REPORTS (신고)
    // ============================================
    @GetMapping("/reports")
    public String reports(@RequestParam(defaultValue = "0") int page,
                          @AuthenticationPrincipal CustomUserDetails admin, Model model) {
        model.addAttribute("reports", adminService.pagePendingReports(page));
        model.addAttribute("adminName", admin.getDisplayName());
        return "admin/reports";
    }

    @PostMapping("/reports/{id}/resolve")
    public String resolveReport(@PathVariable Long id,
                                @RequestParam(defaultValue = "none") String action,
                                @AuthenticationPrincipal CustomUserDetails admin) {
        // [v16 신규] 조치 없이 승인만(none) / 비공개(hidden) / 삭제(deleted) 중 선택해서 처리한다.
        UserEntity actor = userRepository.findById(admin.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        reportService.resolveWithAction(id, ReportActionTaken.valueOf(action), actor);
        return "redirect:/admin/reports";
    }

    @PostMapping("/reports/{id}/dismiss")
    public String dismissReport(@PathVariable Long id) {
        reportService.dismiss(id);
        return "redirect:/admin/reports";
    }
}
