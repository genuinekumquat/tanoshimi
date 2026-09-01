package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import java.util.List;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.IntroUpdateRequest;
import net.datasa.tanoshimi.domain.dto.MyTripView;
import net.datasa.tanoshimi.domain.dto.TravelHeatmapView;
import net.datasa.tanoshimi.domain.entity.MyTripEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.FileStorageService;
import net.datasa.tanoshimi.service.FollowService;
import net.datasa.tanoshimi.service.MyTripService;
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

    /** [v20 신규] 마이페이지 본문에 보이는 "내 여행" 미리보기 개수. 전체는 /mypage/mytrip. */
    private static final int TRIP_PREVIEW_SIZE = 5;

    /**
     * [v20-7 신규] /mypage/mytrip 카드 목록을 게시판처럼 페이지당 20개씩 나눠 보여준다.
     * DB 페이징(Pageable)을 새로 만들지 않고, 이미 전체를 한 번에 읽어온 myTrips 리스트를
     * 메모리에서 잘라 쓴다 - 이 리스트는 어차피 글쓰기 모달의 "여행 선택" 드롭다운(myTripViews,
     * 전체 유지 필수)와 isCountable/칭호 동기화(titleService.syncTitles)에도 그대로
     * 필요해서 한 번 더 DB를 왕복할 이유가 없다. 이 프로젝트 규모(팀/학습용 데모)에서
     * 사용자 한 명의 여행 수가 수천 건까지 갈 일은 없다고 보고 내린 선택.
     */
    private static final int TRIP_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PostService postService;
    private final FollowService followService;
    private final FileStorageService fileStorageService;
    private final TitleService titleService;
    private final TravelHeatmapService travelHeatmapService;
    private final MyTripService myTripService;
    private final net.datasa.tanoshimi.service.BlockService blockService;

    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // [v19] 여행 횟수/지도/칭호 판정의 단일 근거 - 파티 완료 자동 등록까지 여기서 동기화된다
        // (멱등, MyTripService 참고). 화면에는 스냅 없는 PARTY 여행도 그대로 보여주되(myTrips),
        // 실제 집계(heatmap·칭호)에는 isCountable 을 통과한 것만 넣는다 - 파티를 완료 처리만
        // 해두고 실제로 안 갔어도 여행 기록이 쌓이는 걸 막기 위함(2026-09-01 요청).
        List<MyTripEntity> myTrips = myTripService.listMine(me);
        List<MyTripEntity> countableTrips = myTrips.stream().filter(myTripService::isCountable).toList();

        TravelHeatmapView heatmap = travelHeatmapService.summarize(countableTrips);

        // 마지막 방문 이후 파티가 완료됐을 수 있으니 이 시점에 칭호를 맞춰준다.
        // 멱등이라 몇 번을 호출해도 같은 결과이며, 원래는 파티 완료 스케줄러(⑤)에서
        // 한 번만 부르는 게 맞다 - TitleService 주석 참고.
        titleService.syncTitles(me, countableTrips);

        List<MyTripView> tripViews = myTrips.stream()
                .map(t -> MyTripView.of(t, myTripService.isCountable(t)))
                .toList();
        // [v20 신규] 마이페이지 본문에는 최근 5개만 미리보기로 보여주고, 전체 목록과
        // 추가·수정·삭제는 /mypage/mytrip 별도 페이지로 뺐다(칭호 관리와 같은 이유 -
        // myTitles(s 취득분 칩)과 달리 전체 카탈로그가 필요한 칭호 관리 화면을 따로 뺀 것과
        // 대응). myTripViews 자체는 자르지 않고 그대로 둔다 - 글쓰기 모달의 "여행 선택"
        // 드롭다운은 최근 5개가 아니라 전체 여행 중에서 고를 수 있어야 하기 때문이다.
        model.addAttribute("myTripViews", tripViews);
        model.addAttribute("myTripPreview", tripViews.stream().limit(TRIP_PREVIEW_SIZE).toList());
        model.addAttribute("myTripTotal", tripViews.size());

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

        return "mypage/index";
    }

    /**
     * [v20 신규] 칭호 관리 - 예전에는 /mypage 안에서 JS로 #view-titles 를 보여주고 감추는
     * 방식이었다. 주소가 항상 /mypage 그대로라 뒤로가기를 누르면 칭호 관리 화면이 아니라
     * 브라우저 히스토리상 그 전 페이지(보통 메인 페이지)로 나가버리는 문제가 있었다 - 실제
     * URL이 있는 별도 라우트로 분리해서 뒤로가기가 /mypage 로 정상 동작하게 한다.
     */
    @GetMapping("/mypage/titles")
    public String myPageTitles(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이 페이지에 직접 들어왔을 수도 있으니(북마크 등) 마이페이지와 마찬가지로 칭호를
        // 한 번 맞춰준다 - TitleService 주석 참고, 멱등이라 안전하다.
        List<MyTripEntity> countableTrips = myTripService.listMine(me).stream()
                .filter(myTripService::isCountable).toList();
        titleService.syncTitles(me, countableTrips);

        model.addAttribute("me", me);
        model.addAttribute("myTitle", titleService.latestTitle(me));
        // 칭호 관리 화면은 미획득분까지 보여줘야 해서 전체 목록도 함께 넘긴다.
        // 보유 여부는 코드 문자열로 대조한다 - 엔티티는 조회 단위가 달라 동일성 비교가 안 된다.
        model.addAttribute("allTitles", titleService.allTitlesOrdered());
        model.addAttribute("ownedCodes", titleService.ownedCodes(me));

        return "mypage/titles";
    }

    /**
     * [v20 신규] "내 여행" 전체 목록 + 추가·수정·삭제. 칭호 관리와 같은 이유로 별도
     * 라우트로 뺐다(마이페이지 본문에는 미리보기 5개만).
     *
     * <p><b>[v20-4]</b> "스냅 인증 대기"인 PARTY 여행을 카운트로 바꾸는 유일한 방법은
     * 이 화면에서 그 여행을 골라 새 스냅을 쓰는 것(글쓰기 모달 "여행 선택" → 그 여행에
     * 자동 연결, PostService.write 참고)뿐이다. 이미 올려둔 스냅을 소급 연결해주는
     * 기능(v20-2)은 걷어냈다 - MyTripService.isCountable 주석 참고.
     *
     * <p><b>[v20-7]</b> 카드 목록에 게시판 스타일 페이지네이션(20개씩)을 추가했다.
     * {@code myTripViews}는 여전히 전체 목록을 담아 글쓰기 모달의 "여행 선택" 드롭다운에
     * 쓰이고(어느 페이지에 있는 여행이든 선택할 수 있어야 함), 화면에 카드로 보여줄 목록만
     * {@code myTripPageViews}로 따로 잘라 넘긴다(TRIP_PAGE_SIZE 주석 참고).
     */
    @GetMapping("/mypage/mytrip")
    public String myPageTrips(@RequestParam(defaultValue = "0") int page,
                              @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<MyTripEntity> myTrips = myTripService.listMine(me);
        List<MyTripEntity> countableTrips = myTrips.stream().filter(myTripService::isCountable).toList();
        titleService.syncTitles(me, countableTrips);

        List<MyTripView> tripViews = myTrips.stream()
                .map(t -> MyTripView.of(t, myTripService.isCountable(t)))
                .toList();

        int totalPages = Math.max(1, (int) Math.ceil(tripViews.size() / (double) TRIP_PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int from = Math.min(currentPage * TRIP_PAGE_SIZE, tripViews.size());
        int to = Math.min(from + TRIP_PAGE_SIZE, tripViews.size());

        model.addAttribute("me", me);
        model.addAttribute("myTripViews", tripViews);
        model.addAttribute("myTripPageViews", tripViews.subList(from, to));
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);

        return "mypage/mytrip";
    }

    /**
     * [v21 신규] "그 지역 스냅 모아보기".
     *
     * <p>지도에서 더 들어갈 데가 없는 지역(상세 지도의 시/군·현, 드릴다운이 없는 시/도)을
     * 클릭하면 여기로 온다 - 그 지역 태그가 붙은 내 스냅만 모아 보여주고, 스냅을 누르면
     * 해당 게시글(/board/{id})로 간다. 지역 이름은 지도 쪽 표기(예: "울릉")로 넘어오는데
     * 글에 적힌 표기("독도", "경상북도" 등)와 다를 수 있어서, 비교는 PostService 에서
     * 정규화한 뒤에 한다(PostService.normalizeRegion).
     */
    @GetMapping("/mypage/snaps")
    public String regionSnaps(@RequestParam(name = "region", required = false) String region,
                              @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        model.addAttribute("me", me);
        model.addAttribute("region", region == null ? "" : region.trim());
        model.addAttribute("snaps", postService.regionSnaps(me, region));

        return "mypage/snaps";
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
