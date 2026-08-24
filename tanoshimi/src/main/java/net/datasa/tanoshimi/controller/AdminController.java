package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.BannerEntity;
import net.datasa.tanoshimi.domain.entity.ReportStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.BannerRepository;
import net.datasa.tanoshimi.repository.ReportRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.FileStorageService;
import net.datasa.tanoshimi.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.TourEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.repository.TourRepository;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final TourRepository tourRepository;
    private final BannerRepository bannerRepository;
    private final FileStorageService fileStorageService;
    private final ReportRepository reportRepository;
    private final ReportService reportService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal CustomUserDetails admin,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        Page<UserEntity> users;
        if (keyword != null && !keyword.trim().isEmpty()) {
            users = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    keyword, keyword,
                    PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id")));
        } else {
            users = userRepository.findAll(
                    PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id")));
        }
        
        model.addAttribute("keyword", keyword);
        model.addAttribute("users", users);
        model.addAttribute("adminName", admin.getDisplayName());
        
        // 배너 목록
        model.addAttribute("banners", bannerRepository.findAllByOrderBySortOrderAsc());
        // 대기 중인 신고 개수 - 관리자 메뉴에 뱃지로 보여주기 위함
        model.addAttribute("pendingReportCount", reportRepository.countByStatus(ReportStatus.pending));
        return "admin/users";
    }

    @PostMapping("/users/{id}/suspend")
    @Transactional
    public String suspend(@PathVariable Long id, @RequestParam int duration, @AuthenticationPrincipal CustomUserDetails admin) {
        userRepository.findById(id).ifPresent(user -> {
            if (!user.getId().equals(admin.getId())) {
                LocalDateTime until = LocalDateTime.now();
                if (duration == 999) {
                    until = until.plusYears(999);
                } else if (duration == 1) {
                    until = until.plusHours(24);
                } else {
                    until = until.plusDays(duration);
                }
                user.suspend(until);
            }
        });
        return "redirect:/admin";
    }

    @PostMapping("/users/{id}/activate")
    @Transactional
    public String activate(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(UserEntity::activate);
        return "redirect:/admin";
    }

    @GetMapping("/parties")
    @Transactional(readOnly = true)
    public String parties(@RequestParam(required = false) String keyword, 
                          @RequestParam(defaultValue = "0") int page, 
                          Model model, 
                          @AuthenticationPrincipal CustomUserDetails admin) {
        Page<PartyEntity> parties;
        if (keyword != null && !keyword.trim().isEmpty()) {
            parties = partyRepository.findByTitleContainingIgnoreCase(
                    keyword, PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id")));
        } else {
            parties = partyRepository.findAll(PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id")));
        }
        parties.forEach(p -> {
            if (p.getOwner() != null) {
                p.getOwner().getName(); // Initialize proxy
            }
        });
        model.addAttribute("keyword", keyword);
        model.addAttribute("parties", parties);
        model.addAttribute("adminName", admin.getDisplayName());
        return "admin/parties";
    }

    @PostMapping("/parties/{id}/close")
    @Transactional
    public String closeParty(@PathVariable Long id) {
        partyRepository.findById(id).ifPresent(party -> party.close());
        return "redirect:/admin/parties";
    }

    @GetMapping("/tours/create")
    public String createTourForm(@AuthenticationPrincipal CustomUserDetails admin, Model model) {
        model.addAttribute("adminName", admin.getDisplayName());
        return "admin/tour_create";
    }

    @PostMapping("/tours")
    @Transactional
    public String createTour(
            @RequestParam String title, @RequestParam String region,
            @RequestParam int priceKrw, @RequestParam int priceJpy,
            @RequestParam byte durationNights, @RequestParam String description,
            @RequestParam byte minParticipants, @RequestParam byte maxParticipants,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkinTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkoutTime,
            @RequestParam VenueType venueType) {
            
        TourEntity tour = TourEntity.builder()
            .title(title)
            .region(region)
            .priceKrw(priceKrw)
            .priceJpy(priceJpy)
            .durationNights(durationNights)
            .description(description)
            .minParticipants(minParticipants)
            .maxParticipants(maxParticipants)
            .checkinTime(checkinTime)
            .checkoutTime(checkoutTime)
            .venueType(venueType)
            .status(ActiveStatus.active)
            .build();
            
        tourRepository.save(tour);
        return "redirect:/packages";
    }

    @PostMapping("/users/{id}/grant-admin")
    @Transactional
    public String grantAdmin(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(UserEntity::grantAdmin);
        return "redirect:/admin";
    }
    
    @PostMapping("/users/{id}/revoke-admin")
    @Transactional
    public String revokeAdmin(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        userRepository.findById(id).ifPresent(user -> {
            if (!user.getId().equals(admin.getId())) {
                user.revokeAdmin();
            }
        });
        return "redirect:/admin";
    }
    
    // ============================================
    // BANNERS
    // ============================================
    @GetMapping("/banners")
    public String banners(@AuthenticationPrincipal CustomUserDetails admin, Model model) {
        model.addAttribute("adminName", admin.getDisplayName());
        model.addAttribute("banners", bannerRepository.findAllByOrderBySortOrderAsc());
        return "admin/banners";
    }

    @PostMapping("/banners")
    @Transactional
    public String uploadBanner(@RequestParam MultipartFile file, @RequestParam(required=false) String targetUrl) {
        if (!file.isEmpty()) {
            List<BannerEntity> currentBanners = bannerRepository.findAllByOrderBySortOrderAsc();
            if (currentBanners.size() >= 5) {
                return "redirect:/admin/banners?error=bannerLimit";
            }
            String url = fileStorageService.saveImage(file);
            BannerEntity banner = new BannerEntity();
            banner.setImageUrl(url);
            banner.setTargetUrl(targetUrl);
            banner.setSortOrder(currentBanners.size());
            bannerRepository.save(banner);
        }
        return "redirect:/admin/banners";
    }
    
    @PostMapping("/banners/{id}/delete")
    @Transactional
    public String deleteBanner(@PathVariable Long id) {
        bannerRepository.deleteById(id);
        return "redirect:/admin/banners";
    }

    // ============================================
    // REPORTS (신고)
    // ============================================
    @GetMapping("/reports")
    public String reports(@RequestParam(defaultValue = "0") int page,
                          @AuthenticationPrincipal CustomUserDetails admin, Model model) {
        model.addAttribute("reports", reportRepository.findByStatusOrderByCreatedAtDesc(
                ReportStatus.pending, PageRequest.of(page, 20)));
        model.addAttribute("adminName", admin.getDisplayName());
        return "admin/reports";
    }

    @PostMapping("/reports/{id}/resolve")
    public String resolveReport(@PathVariable Long id) {
        reportService.resolve(id);
        return "redirect:/admin/reports";
    }

    @PostMapping("/reports/{id}/dismiss")
    public String dismissReport(@PathVariable Long id) {
        reportService.dismiss(id);
        return "redirect:/admin/reports";
    }
}