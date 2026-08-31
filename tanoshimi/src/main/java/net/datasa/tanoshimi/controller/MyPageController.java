package net.datasa.tanoshimi.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.ReservationRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.FileStorageService;
import net.datasa.tanoshimi.service.FollowService;
import net.datasa.tanoshimi.service.PostService;
import net.datasa.tanoshimi.service.TitleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final UserRepository userRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final ReservationRepository reservationRepository;
    private final PostService postService;
    private final FollowService followService;
    private final FileStorageService fileStorageService;
    private final TitleService titleService;

    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        model.addAttribute("me", me);
        model.addAttribute("myParties", partyMemberRepository.findByUserOrderByJoinedAtDesc(me));
        model.addAttribute("myPosts", postService.myPosts(me, PageRequest.of(0, 12)));
        model.addAttribute("followerCount", followService.followerCount(me));
        model.addAttribute("followingCount", followService.followingCount(me));

        List<ReservationEntity> reservations = reservationRepository.findByBookedBy(me);
        Map<String, Integer> regionVisits = new HashMap<>();
        for (ReservationEntity r : reservations) {
            String region = r.getTour().getRegion();
            // simple mapping to match map-data.js keys
            String mappedRegion = mapKoreanToKey(region);
            regionVisits.put(mappedRegion, regionVisits.getOrDefault(mappedRegion, 0) + 1);
        }
        model.addAttribute("regionVisits", regionVisits);
        model.addAttribute("myTitle", titleService.latestTitle(me));

        return "mypage/index";
    }

    private String mapKoreanToKey(String region) {
        return switch (region) {
            case "오사카" -> "osaka";
            case "도쿄" -> "tokyo";
            case "교토" -> "kyoto";
            case "후쿠오카" -> "fukuoka";
            case "홋카이도" -> "hokkaido";
            case "오키나와" -> "okinawa";
            case "서울", "경기", "인천" -> "capital";
            case "부산", "경남" -> "gyeongnam";
            case "제주", "제주도" -> "jeju";
            case "강원", "강원도" -> "gangwon";
            default -> region;
        };
    }

    @PostMapping("/api/mypage/profile-image")
    @ResponseBody
    @Transactional
    public ApiResponse<String> uploadProfileImage(@RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String url = fileStorageService.saveProfileImage(file);
        
        // 1. 엔티티 업데이트
        me.changeProfile(me.getName(), me.getIntro(), url);
        fileStorageService.markActive(url);
        
        // 2. 세션 업데이트 (프로필 이미지가 전역적으로 즉시 반영되게 하기 위함)
        CustomUserDetails newUserDetails = new CustomUserDetails(me, principal.getAttributes());
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken newAuth = 
                new UsernamePasswordAuthenticationToken(newUserDetails, currentAuth.getCredentials(), currentAuth.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);
        
        return ApiResponse.ok("프로필 사진이 변경되었습니다.", url);
    }

    @GetMapping("/users/{id}")
    public String publicProfile(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity target = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        model.addAttribute("profileUser", target);
        model.addAttribute("followerCount", followService.followerCount(target));
        model.addAttribute("followingCount", followService.followingCount(target));
        if (principal != null) {
            UserEntity me = userRepository.findById(principal.getId()).orElse(null);
            model.addAttribute("isFollowing", me != null && followService.isFollowing(me, target));
            model.addAttribute("isSelf", me != null && me.getId().equals(target.getId()));
        }
        return "mypage/public-profile";
    }
}