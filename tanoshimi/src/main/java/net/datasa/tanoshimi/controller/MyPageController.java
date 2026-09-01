package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.IntroUpdateRequest;
import net.datasa.tanoshimi.domain.dto.TravelHeatmapView;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.FileStorageService;
import net.datasa.tanoshimi.service.FollowService;
import net.datasa.tanoshimi.service.PostService;
import net.datasa.tanoshimi.service.TitleService;
import net.datasa.tanoshimi.service.TravelHeatmapService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/** 마이페이지 · 공개 프로필. 담당: 김민규(⑥). */
@Controller
@RequiredArgsConstructor
public class MyPageController {

    /** users.intro 가 VARCHAR(300) - 화면 maxlength 와 같은 값으로 서버에서도 막는다. */
    private static final int INTRO_MAX_LENGTH = 300;

    private final UserRepository userRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PostService postService;
    private final FollowService followService;
    private final FileStorageService fileStorageService;
    private final TitleService titleService;
    private final TravelHeatmapService travelHeatmapService;
    private final net.datasa.tanoshimi.service.BlockService blockService;

    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        TravelHeatmapView heatmap = travelHeatmapService.summarize(me);

        // 마지막 방문 이후 파티가 완료됐을 수 있으니 이 시점에 칭호를 맞춰준다.
        // 멱등이라 몇 번을 호출해도 같은 결과이며, 원래는 파티 완료 스케줄러(⑤)에서
        // 한 번만 부르는 게 맞다 - TitleService 주석 참고.
        titleService.syncTitles(me);

        model.addAttribute("me", me);
        model.addAttribute("myParties", partyMemberRepository.findByUserOrderByJoinedAtDesc(me));
        model.addAttribute("myPosts", postService.myPosts(me, PageRequest.of(0, 12)));
        // 지도에서 지역에 마우스를 올렸을 때 띄울 스냅(지역 태그가 붙은 내 글).
        // 피드보다 넓게 가져오되, 지도 위에 최대 10장만 뿌리므로 60개면 충분하다.
        model.addAttribute("snapPosts", postService.regionTaggedPosts(me, 60));
        model.addAttribute("followerCount", followService.followerCount(me));
        model.addAttribute("followingCount", followService.followingCount(me));
        // heatmap 자체는 인라인 JS 로 직렬화(Jackson)해서 넘기고, 화면에 글자로 찍는 숫자는
        // 따로 담는다. record 접근자(totalTrips())를 Thymeleaf 표현식에서 바로 쓰는 건
        // 이 프로젝트에 선례가 없어 안전한 쪽을 택했다.
        model.addAttribute("heatmap", heatmap);
        model.addAttribute("totalTrips", heatmap.totalTrips());
        model.addAttribute("visitedRegions", heatmap.visitedRegions());
        model.addAttribute("myTitle", titleService.latestTitle(me));
        model.addAttribute("myTitles", titleService.ownedTitles(me));
        // 칭호 관리 화면은 미획득분까지 보여줘야 해서 전체 목록도 함께 넘긴다.
        // 보유 여부는 코드 문자열로 대조한다 - 엔티티는 조회 단위가 달라 동일성 비교가 안 된다.
        model.addAttribute("allTitles", titleService.allTitlesOrdered());
        model.addAttribute("ownedCodes", titleService.ownedCodes(me));

        return "mypage/index";
    }

    @PostMapping("/api/mypage/profile-image")
    @ResponseBody
    @Transactional
    public ApiResponse<String> uploadProfileImage(@RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String url = fileStorageService.saveProfileImage(file);

        // 1. 엔티티 업데이트
        me.changeProfile(me.getName(), me.getIntro(), url);

        // 2. 세션 업데이트 (프로필 이미지가 전역적으로 즉시 반영되게 하기 위함)
        CustomUserDetails newUserDetails = new CustomUserDetails(me, principal.getAttributes());
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken newAuth =
                new UsernamePasswordAuthenticationToken(newUserDetails, currentAuth.getCredentials(), currentAuth.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        return ApiResponse.ok("프로필 사진이 변경되었습니다.", url);
    }

    /**
     * 자기소개 저장.
     *
     * <p>이 API 가 없어서 화면의 자기소개 편집기가 DOM 텍스트만 바꾸고 끝나는 상태였다
     * (새로고침하면 원래대로 돌아감). 저장 경로를 붙여 실제로 반영되게 한다.
     */
    @PostMapping("/api/mypage/intro")
    @ResponseBody
    @Transactional
    public ApiResponse<?> updateIntro(@RequestBody IntroUpdateRequest request,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        String intro = request.intro() == null ? "" : request.intro().trim();
        if (intro.length() > INTRO_MAX_LENGTH) {
            return ApiResponse.fail("자기소개는 " + INTRO_MAX_LENGTH + "자까지 쓸 수 있어요.");
        }

        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        me.changeProfile(me.getName(), intro, me.getProfileImageUrl());

        return ApiResponse.ok("자기소개를 저장했습니다.", intro);
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
            model.addAttribute("isBlocked", me != null && blockService.isBlockedByMe(me, target));
        }
        return "mypage/public-profile";
    }
}
